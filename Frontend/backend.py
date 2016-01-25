from _cfg import env
import socket
import json
import time
import threading
from queue import Queue, Empty
from weakref import WeakSet
from database import db, Game, AI, Lang
from logger import logger

from pprint import pprint


class Backend(threading.Thread):
	daemon = True
	game_update_queues = WeakSet()
	sleep_time = 60
	queued_for_reconnect = Queue()
	suppress_connection_warnings = False
	app = None
	sock = None
	connected = False
	requests = {}
	latest_request_id = 0

	def __init__(self):
		threading.Thread.__init__(self)
		self.connect()
		self.start()

	def is_connected(self):
		return self.sock and self.connected

	def connect(self):
		logger.info('Verbinde zum Backend @ {}:{}'.format(env.backend_url, env.backend_port))
		try:
			self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
			self.sock.connect((env.backend_url, env.backend_port))
			self.sock.sendall(b"")
			self.connected = True
			if self.queued_for_reconnect.qsize():
				logger.info("Sende {} gequeuete Nachrichten".format(self.queued_for_reconnect.qsize()))
				while not self.queued_for_reconnect.empty():
					self.send_dict(self.queued_for_reconnect.get())
		except socket.error as e:
			logger.warning(e)
			self.sock = None
			self.connected = False

	def request_compile(self, ai):
		if ai.latest_version().frozen:
			logger.warning("request_compile mit freigegebener KI aufgerufen!")
		reqid = self.latest_request_id
		self.latest_request_id += 1
		d = {
			'action': 'compile', 'id':str(ai.id)+'v'+str(ai.latest_version().version_id),
			'requestid': reqid, 'gametype': ai.type.id, 'language': ai.latest_version().lang.name
		}
		self.requests[reqid] = d
		self.send_dict(d)
		self.requests[reqid]["queue"] = Queue()
		logger.info("Backend [{}]: Kompilierung von {} gestartet".format(reqid, ai.name))
		return reqid


	def compile(self, ai):
		reqid = self.request_compile(ai)
		yield ("compiling", "status")
		yield ("F: Kompilierung mit ID {} angefangen.\n".format(reqid), "set_text")

		timed_out = 0
		char_before_timeout = None
		while True:
			resp = backend.lock_for_req(reqid, timeout=1)
			b_req = backend.request(reqid)
			if not resp:
				yield (".", "log")
				timed_out += 1
				if timed_out > 30:
					logger.warning("compile job timed out")
					yield ("\nDas Backend sendet nichts.", "log")
					yield ("\nVersuch es nochmal.", "log")
					yield ("Das Backend sendet nichts.", "error")
					return
			else:
				if timed_out > 0:
					if char_before_timeout == "\n":
						yield ("\n", "log")
					else:
						yield (" ", "log")
				timed_out = 0
				if "success" in resp:
					if resp["success"]:
						yield ("Anfrage erfolgreich beendet\n", "log")
						ai.latest_version().compiled = True
						db.session.commit()
					else:
						ai.latest_version().compiled = False
						db.session.commit()
						yield ("Kompilierung fehlgeschlagen\n", "log")
						if "exception" in resp:
							yield (resp["exception"], "log")
						yield ("Kompilierung fehlgeschlagen", "error")
					return
				elif "status" in resp:
					if resp["status"] == "processed":
						yield ("Anfrage angefangen\n", "log")
				elif "compilelog" in resp:
					yield (resp["compilelog"], "log")
					if len(resp["compilelog"]) > 0:
						char_before_timeout = resp["compilelog"][-1]
				else:
					# Falls die Antwort vom Backend nicht verstanden wurde.
					yield ("B: " + str(resp) + "\n", "log")


	def request_game(self, ais):
		reqid = self.latest_request_id
		self.latest_request_id += 1
		if any([ai.type != ais[0].type for ai in ais]):
			raise RuntimeError("AIs haben verschiedene Typen: " + str(ais))
		d = {'action': 'start', 'ais': [], 'languages': [], 'gametype': ais[0].type.id, 'requestid': reqid}
		for ai in ais:
			if not ai.active_version():
				logger.debug(ais)
				logger.debug(ai)
				raise RuntimeError("Nich fertige KI in request_game()")
			d['ais'].append(str(ai.id) + 'v' + str(ai.active_version().version_id))
			d['languages'].append(ai.active_version().lang.name)
		self.requests[reqid] = d
		self.send_dict(d)
		self.requests[reqid]["queue"] = Queue()
		self.requests[reqid]["queues"] = WeakSet()
		self.requests[reqid]["ai0"] = ais[0]
		self.requests[reqid]["ai1"] = ais[1]
		self.requests[reqid]["ai_objs"] = ais
		self.requests[reqid]["states"] = []
		self.requests[reqid]["crashes"] = []
		self.requests[reqid]["status_text"] = "In Wartschlange"
		logger.info("Backend[{}]: Spiel mit {} gestartet".format(reqid, [ai.name for ai in ais]))
		return reqid

	def request_qualify(self, ai):
		if ai.latest_version().frozen:
			logger.error("request_qualify mit freigegebener KI aufgerufen!")

		quali_lang = None
		for lname in env.quali_lang_hierarchy:
			quali_lang = Lang.query.filter(Lang.name == lname).first()
			if lang:
				break
		if not lang:
			quali_lang = Lang.query.first()

		logger.info("Erwarte, dass Quali-KI als {}-SimplePlayer kompiliert wurde".format(quali_lang.name))

		reqid = self.latest_request_id
		self.latest_request_id += 1
		d = {'action': 'qualify', 'id': str(ai.id)+'v'+str(ai.latest_version().version_id),
			'gametype': ai.type.id, "language": ai.latest_version().lang.name,
			"requestid": reqid, "qualilang": quali_lang.name}
		self.requests[reqid] = d
		self.send_dict(d)
		self.requests[reqid]["queue"] = Queue()
		self.requests[reqid]["queues"] = WeakSet()
		self.requests[reqid]["ai0"] = ai
		self.requests[reqid]["ai1"] = AI.query.get(-ai.type.id)
		self.requests[reqid]["ai_objs"] = [ai, AI.query.get(-ai.type.id)]
		self.requests[reqid]["states"] = []
		self.requests[reqid]["crashes"] = []
		logger.info("Backend[{}]: Quali-Spiel mit '{}' ({}) gestartet".format(reqid, ai.name, ai.lang.name))
		return reqid

	def request_tournament(self, tournament):
		reqid = self.latest_request_id
		self.latest_request_id += 1
		d = {'action': 'tournament', 'tournament': tournament.id, 'gametype': tournament.type.id, 'requestid': reqid}
		self.requests[reqid] = d
		self.send_dict(d)
		logger.info("Backend[{}]: Turnier {} gestartet".format(reqid, str(tournament)))
		self.requests[reqid]["tournament_object"] = tournament
		self.requests[reqid]["games"] = {}
		return reqid

	def send_dict(self, d):
		if not self.is_connected():
			self.connect()
		if self.is_connected():
			try:
				self.sock.sendall(bytes(json.dumps(d) + "\n", "utf-8"))
				return True
			except socket.error as e:
				logger.exception(e)
				self.connected = False
				logger.info("Queueing message due to send error: " + str(d))
				self.queued_for_reconnect.put(d)
		else:
			logger.info("Queued Backend-Message: " + str(d))
			self.queued_for_reconnect.put(d)
		return False

	def parse(self, d):
		if not "requestid" in d:
			logger.warning("Invalid Response!")
			pprint(d)
			return

		reqid = d["requestid"]

		if not reqid in self.requests:
			logger.warning("Requestid isnt known ({})".format(reqid))
			pprint(d)
			return

		#logger.info("Backend [{}]: {}".format(reqid, d))
		pprint(d)

		if self.requests[reqid]["action"] == "tournament":
			self.handleTournament(self.requests[reqid], d)
			return

		self.requests[reqid].update(d)

		if self.handleGame(self.requests[reqid], d):
			return

		if "queue" in self.requests[reqid]:
			self.requests[reqid]["queue"].put(d)
		if "queues" in self.requests[reqid]:
			for q in self.requests[reqid]["queues"]:
				q.put(d)

	def handleGame(self, full, delta):
		if delta.get("isCrash"):
			delta["step"] = len(full["states"])
			if "queues" in full:
				for queue in full["queues"]:
					queue.put(delta)
			full["crashes"].append(delta)
			return True

		if full["action"] in ["start", "qualify"]:
			if "success" in delta and full["action"] == "start":
				if not self.app:
					raise RuntimeError("Spiel vor verbindung mit App")
				with self.app.app_context():
					logger.info("game finished!")
					g = Game.from_inprogress(full)
					logger.debug(g)
					if g:
						full["finished_game_obj"] = g
					else:
						logger.warning("Game.from_inprogress hat kein Spiel-Objekt zurückgegeben")
						logger.info("Sending exception as crash")
						for ai in [full["ai0"], full["ai1"]]:
							self.handleGame(full, {
								"isCrash": True,
								"requestid": delta["requestid"],
								"reason": delta.get("exception", "reason missing"),
								"id": str(ai.id)+'v'+str(ai.latest_version().version_id),
								"step": 0
							})

			if "data" in delta:
				delta["data"]["calculationPoints"] = delta["calculationPoints"]
				full["states"].append(delta["data"])

			if "status" in delta and delta["status"] == "restarted":
				full["states"] = []
				full["crashes"] = []

			for q in self.game_update_queues:
				q.put(delta)

	def handleTournament(self, full, delta):
		if not "tournament_object" in full:
			return
		if "gameid" in delta and "ais" in delta:
			logger.info("new game in tournament " + delta["gameid"])
			with self.app.app_context():
				ais = [AI.query.get(s.split("v")[0]) for s in delta["ais"]]
			if any([not ai for ai in ais]):
				logger.error("game in tournament missing ais ({})".format(delta["ais"]))
				return
			full["games"][delta["gameid"]] = {
				'action': 'start',
				'ais': delta["ais"],
				'languages': None,
				'gametype': ais[0].type.id,
				'requestid': None,
				"queue": Queue(),
				"queues": WeakSet(),
				"ai0": ais[0],
				"ai1": ais[1],
				"ai_objs": ais,
				"states": [],
				"crashes": [],
				"status_text": "In Wartschlange",
				"tournament": full["tournament_object"]
			}
		elif "uuid" in delta:
			uuid = delta["uuid"]
			if not uuid in full["games"]:
				logger.warning("ignoring unknown uuid")
				return
			self.handleGame(full["games"][uuid], delta)
		elif "exception" in delta:
			logger.error(delta["exception"])
			full["tournament_object"].executed = False
			with self.app.app_context():
				db.session.commit()

	def request(self, reqid):
		if reqid in self.requests:
			return self.requests[reqid]
		logger.warning("request with id " + str(reqid) + " doesnt exist!")

	def lock_for_req(self, reqid, timeout=30):
		try:
			return self.requests[reqid]["queue"].get(timeout=timeout)
		except Empty:
			logger.debug(str(timeout) + " sec timeout for request queue " + str(reqid))
			return False

	def subscribe_game_update(self):
		logger.debug("New SSe")
		logger.debug(len(self.game_update_queues))
		q = Queue()
		self.game_update_queues.add(q)
		return q


	def inprogress_games(self):
		games = []
		for reqid in self.requests:
			r = self.requests[reqid]
			if not r["action"] == "start":
				continue
			if not "status" in r:
				continue
			#if not r["status"] in ["processed", "started"]:
			#	continue

			if "success" in r:
				continue

			games.append(dict(
				id=r["requestid"],
				ai0=r["ai0"],
				ai1=r["ai1"],
				status=r["status_text"],
				inqueue=r["status"] == "processed"
			))

		# handle tournament games
		for req in self.requests.values():
			if "games" in req:
				for k, v in req["games"].items():
					games.append(dict(
						id=v["uuid"],
						ai0=v["ai0"],
						ai1=v["ai1"],
						status=v["status_text"],
						inqueue=v["status"] == "processed"
					))
		return games


	def inprogress_log(self, id):
		if not id in self.requests:
			return False
		if not "queues" in self.requests[id]:
			return False

		for d in self.requests[id]["states"]:
			yield d, "state"

		for d in self.requests[id]["crashes"]:
			yield d, "crash"

		if "finished_game_obj" in self.requests[id]:
			yield (self.requests[id]["finished_game_obj"], "finished_game_obj")

		queue = Queue()
		self.requests[id]["queues"].add(queue)
		while True:
			try:
				update = queue.get(timeout=120)
				d = self.request(id)
				if "progress" in update:
				    update["data"]["progress"] = update["progress"]
				if "data" in update:
					yield update["data"], "state"
				elif "success" in update:
					yield update, "success"
				elif "isCrash" in update and update["isCrash"]:
					yield update, "crash"
				else:
					logger.debug("no data in frame. " + str(update))
				if "finished_game_obj" in d:
					if not d["finished_game_obj"]:
						logger.error("finished_game_obj is None")
						continue
					yield (d["finished_game_obj"], "finished_game_obj")
			except Empty:
				return

	def run(self):
		logger.info("Backend Thread running!")
		self.listen()

	def listen(self):
		while 1:
			if not self.connected:
				self.connect()
			if self.connected:
				self.sleep_time = 5
				r = self.sock.recv(1024*1024).decode("utf-8")
				if r == '':
					self.connected = False
					logger.warning("connection zum backend gestorben")
					time.sleep(1)
					continue
				## zerstückelte blöcke?
				for d in r.split("\n"):
					if d == '':
						continue
					if d == '\n':
						pass
					else:
						self.parse(json.loads(d))
			else:
				self.sleep_time = min(self.sleep_time * 3, 60*60*3)
				if not self.suppress_connection_warnings:
					logger.debug("No connection to Backend; sleeping " + str(self.sleep_time) + " seconds.")
				time.sleep(self.sleep_time)


backend = Backend()
