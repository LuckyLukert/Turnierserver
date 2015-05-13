from flask.ext.sqlalchemy import SQLAlchemy
from flask import send_file, abort
from _cfg import env
from activityfeed import Activity
from io import BytesIO
import ftputil
import arrow

db = SQLAlchemy()

class SyncedFTP:
	def __init__(self):
		self.connect()

	def connect(self):
		Activity("Verbinde zum FTP @ " + env.ftp_url)
		self.ftp_host = ftputil.FTPHost(env.ftp_url, env.ftp_uname, env.ftp_pw)

	def send_file(self, path):
		self.connect()
		if not self.ftp_host.path.exists(path):
			Activity("Datei '" + path + "' existiert auf dem FTP nicht.")
			abort(404)
		with self.ftp_host.open(path, "rb") as remote_obj:
			f = BytesIO(remote_obj.read())
			f.seek(0)
			return send_file(f)

ftp = SyncedFTP()

def db_obj_init_msg(obj):
	import inspect, pprint
	callername = inspect.getouterframes(inspect.currentframe(), 2)[5][3]
	Activity(str(obj) + " erschafft.", extratext="Aufgerufen von '" + callername + "'.")

class User(db.Model):
	__tablename__ = 't_users'
	id = db.Column(db.Integer, primary_key=True)
	name = db.Column(db.String(50), unique=True, nullable=False)
	ai_list = db.relationship("AI", order_by="AI.id", backref="User")

	def __init__(self, *args, **kwargs):
		super(User, self).__init__(*args, **kwargs)
		db_obj_init_msg(self)

	def info(self):
		return {"id": self.id, "name": self.name, "ais": [ai.info() for ai in self.ai_list]}

	def icon(self):
		return ftp.send_file("Users/"+str(self.id)+"/icon.png")

	def __repr__(self):
		return "<User(id={}, name={})".format(self.id, self.name)

	# Flask.Login zeugs
	def is_authenticated(self):
		return True
	def is_active(self):
		return True
	def is_anonymous(self):
		return False
	def get_id(self):
		return self.name


class AI_Game_Assoc(db.Model):
	__tablename__ = 't_ai_game_assoc'
	game_id = db.Column(db.Integer, db.ForeignKey('t_games.id'), primary_key=True)
	ai_id = db.Column(db.Integer, db.ForeignKey('t_ais.id'), primary_key=True)
	role = db.Column(db.String(50))
	ai = db.relationship("AI", backref="game_assocs")


class AI(db.Model):
	__tablename__ = 't_ais'
	id = db.Column(db.Integer, primary_key=True)
	name = db.Column(db.Text, nullable=False)
	desc = db.Column(db.Text)
	user_id = db.Column(db.Integer, db.ForeignKey('t_users.id'))
	user = db.relationship("User", backref=db.backref('t_ais', order_by=id))
	lang_id = db.Column(db.Integer, db.ForeignKey('t_langs.id'))
	lang = db.relationship("Lang", backref=db.backref('t_ais', order_by=id))

	def __init__(self, *args, **kwargs):
		super(AI, self).__init__(*args, **kwargs)
		db_obj_init_msg(self)

	def info(self):
		return {"id": self.id, "name": self.name, "author": self.user.name, "description": self.desc, "lang": self.lang.info()}

	def icon(self):
		return ftp.send_file(("AIs/"+str(self.id)+"/icon.png"))

	def __repr__(self):
		return "<AI(id={}, name={}, user_id={}, lang={}>".format(self.id, self.name, self.user_id, self.lang.name)

class Lang(db.Model):
	__tablename__ = "t_langs"
	id = db.Column(db.Integer, primary_key=True, autoincrement=True)
	name = db.Column(db.Text)
	url = db.Column(db.Text)
	ai_list = db.relationship("AI", order_by="AI.id", backref="Lang")

	def __init__(self, *args, **kwargs):
		super(Lang, self).__init__(*args, **kwargs)
		db_obj_init_msg(self)

	def info(self):
		return {"id": self.id, "name": self.name, "url": self.url}

	def __repr__(self):
		return "<Lang(id={}, name={}, url={}>".format(self.id, self.name, self.url)

class Game(db.Model):
	__tablename__ = 't_games'
	id = db.Column(db.Integer, primary_key=True, autoincrement=True)
	ai_assocs = db.relationship("AI_Game_Assoc", backref="game")
	type = db.Column(db.Integer, nullable=False)
	timestamp = db.Column(db.BigInteger)

	def __init__(self, *args, **kwargs):
		super(Game, self).__init__(*args, **kwargs)
		self.timestamp = arrow.utcnow().timestamp
		db_obj_init_msg(self)

	def time(self, locale):
		return arrow.get(self.timestamp).to('local').humanize(locale=locale)

	def info(self):
		return {"id": self.id, "ais": [assoc.ai.info() for assoc in self.ai_assocs]}

	def __repr__(self):
		return "<Game(id={}, type={})>".format(self.id, self.type)


def populate(count=20):
	r = list(range(1, count+1))
	import random
	db.create_all()

	py = Lang(id=1, name="Python", url="https://www.python.org")

	users = [User(name="testuser"+str(i), id=i) for i in r]
	random.shuffle(users)
	ais = [AI(id=i, user=users[i-1], name="testai"+str(i), desc="Beschreibung", lang=py) for i in r]
	games = [Game(id=i, type=1) for i in r]
	assocs = []
	with open("gametypes.json") as f:
		import json
		gametype = json.load(f)["1"]
	for ri, role in enumerate(gametype['roles'], 1):
		assocs += [AI_Game_Assoc(game_id=games[i-1].id, ai_id=ais[i-ri].id, role=role) for i in r]
	db.session.add_all(users + ais + games + assocs)
	db.session.commit()


if __name__ == '__main__':
	populate(99)
	for user in db.query(User).all():
		print(user)
		print(user.info())