from flask import Blueprint, Response, request, abort, redirect, flash, get_template_attribute, url_for
from flask.ext.login import current_user, login_user, logout_user, LoginManager, UserMixin
from functools import wraps
from queue import Empty
from werkzeug.utils import secure_filename
from sqlalchemy.orm.exc import NoResultFound
import json
import magic
import time
import zipfile
import tempfile
from pprint import pprint
from collections import defaultdict

from database import AI, User, Game, Lang, GameType, db, populate, ftp, Game_inprogress, timestamp
from backend import backend
from commons import authenticated, cache, CommonErrors
from _cfg import env
from activityfeed import Activity
from sse import sse_stream




def json_out(f):
	@wraps(f)
	def wrapper(*args, **kwargs):
		def jsonify_wrap(obj):
			return Response(json.dumps(obj), mimetype='application/json')

		result = f(*args, **kwargs)
		if isinstance(result, tuple):
			# (resp, status_code)
			return jsonify_wrap(result[0]), result[1]
		if isinstance(result, dict):
			return jsonify_wrap(result)
		if isinstance(result, list):
			return jsonify_wrap(result)

		# isnt tuple, dict or list -> must be a Response
		return result

	return wrapper


def admin_required(f):
	@wraps(f)
	def wrapper(*args, **kwargs):
		if current_user:
			if current_user.is_authenticated():
				if current_user.admin:
					return f(*args, **kwargs)
		return CommonErrors.NO_ACCESS
	return wrapper

rate_limit_dict = defaultdict(lambda: [0, 9999])

def rate_limited(f):
	@wraps(f)
	def wrapper(*args, **kwargs):
		if current_user.admin:
			return f(*args, **kwargs)
		rate, per = 100, 30*60
		current = timestamp()
		time_passed = current - rate_limit_dict[current_user.id][0]
		rate_limit_dict[current_user.id][0] = current
		rate_limit_dict[current_user.id][1] += time_passed * (rate/per)
		if rate_limit_dict[current_user.id][1] > rate:
			rate_limit_dict[current_user.id][1] = rate
		if rate_limit_dict[current_user.id][1] < 1.0:
			return {"error": "Too many requests."}, 419
		else:
			rate_limit_dict[current_user.id][1] -= 1
			return f(*args, **kwargs)
	return wrapper

login_manager = LoginManager()
@login_manager.user_loader
def load_user(id):
	return User.query.get(id)


api = Blueprint("api", __name__, url_prefix="/api")

@api.route("/", methods=["GET"])
def api_index():
	return "PONG!"

@api.route("/ais", methods=["GET"])
@api.route("/ais/<int:gametype>", methods=["GET"])
@api.route("/ais/<string:gametype>", methods=["GET"])
@json_out
def api_ais(gametype=None):
	if gametype:
		if isinstance(gametype, int):
			gametype = GameType.query.get(gametype)
		elif isinstance(gametype, str):
			gametype = GameType.query.filter(GameType.name == gametype).first()
		else:
			return CommonErrors.BAD_REQUEST
		if not gametype:
			return CommonErrors.BAD_REQUEST

	return [ai.info() for ai in AI.filtered(gametype).all()]

@api.route("/ai/<int:id>", methods=["GET"])
@json_out
def api_ai(id):
	ai = AI.query.get(id)
	if ai:
		return ai.info()
	else:
		return CommonErrors.INVALID_ID

@api.route("/ai/<int:id>/games", methods=["GET"])
@json_out
def api_ai_games(id):
	ai = AI.query.get(id)
	if ai:
		return [assoc.game.info() for assoc in ai.game_assocs]
	else:
		return CommonErrors.INVALID_ID

@api.route("/games")
@api.route("/games/<int:gametype>")
@api.route("/games/<string:gametype>")
@json_out
def api_games(gametype=None):
	if not gametype:
		gametype = GameType.latest()
	else:
		if isinstance(gametype, int):
			gametype = GameType.query.get(gametype)
		elif isinstance(gametype, str):
			gametype = GameType.query.filter(GameType.name == gametype).first()
		else:
			return CommonErrors.BAD_REQUEST
		if not gametype:
			return CommonErrors.BAD_REQUEST
	return [game.info() for game in Game.query.filter(Game.type == gametype).all()]

@api.route("/game/<int:id>", methods=["GET"])
@json_out
def api_game(id):
	game = Game.query.get(id)
	if game:
		return game.info()
	else:
		return CommonErrors.INVALID_ID


@api.route("/game/<int:id>/log")
@sse_stream
def game_log(id):
	game = Game.query.get(id)
	if game:
		for i, chunk in enumerate(game.log):
			chunk["progress"] = i/len(game.log)
			yield json.dumps(chunk), "state"
		yield "", "finished_transmitting"

	else:
		return CommonErrors.INVALID_ID

@api.route("/game/inprogress/<int:id>/log")
@sse_stream
def game_inprogress_log(id):
	gen = backend.inprogress_log(id)
	try:
		d, s = next(gen)
		yield json.dumps(d), s
	except StopIteration:
		return CommonErrors.INVALID_ID

	for data, data_type in gen:
		if data_type == "state":
			yield json.dumps(data), data_type
		elif data_type == "finished_game_obj":
			yield url_for("anonymous.game", id=data.id), "game_finished"
		else:
			print("invalid log_sse type:", data_type, data)


@api.route("/users", methods=["GET"])
@json_out
def api_users():
	return [user.info() for user in User.query.all()]

@api.route("/user/<int:id>", methods=["GET"])
@json_out
def api_user(id):
	user = User.query.get(id)
	if user:
		return user.info()
	else:
		return CommonErrors.INVALID_ID

@api.route("/user/<int:id>/icon", methods=["GET"])
@cache.memoize(timeout=env.cache_max_age)
def user_icon(id):
	user = User.query.get(id)
	if user:
		return user.icon()
	else:
		abort(404)


@api.route("/user/<int:id>/update", methods=["POST"])
@json_out
@authenticated
def api_user_update(id):
	user = User.query.get(id)
	if not user:
		return CommonErrors.INVALID_ID

	if not current_user.can_access(user):
		return CommonErrors.NO_ACCESS

	a = Activity("User " + user.name + " geaendert")
	a.extratext = str(user) + " -> "

	user.firstname = request.form.get('firstname', user.firstname)
	user.lastname = request.form.get('lastname', user.lastname)
	user.email = request.form.get('email', user.email)
	if "password" in request.form:
		user.set_pw(request.form["password"])


	# es muss zur Datenbank geschrieben werden, um die aktuellen Infos zu bekommen
	db.session.commit()

	a.extratext += str(user)

	flash("Änderungen gespeichert.", "info")

	return {"error": False, "user": user.info()}, 200


@api.route("/user/password_reset", methods=["GET", "POST"])
@json_out
def api_user_password_reset():
	username = request.form.get('username')
	email = request.form.get('email')
	if not username or not email:
		return {"error": "No email or username specified."}, 400
	user = User.query.filter(User.email.ilike(email)).filter(User.name.ilike(username)).first()
	if not user:
		return {"error": "Invalid email or username."}, 400
	if not user.validated:
		return {"error": "User not validated"}, 400

	if not user.send_password_reset():
		return {"error": "Password reset failed."}, 500

	flash("Guck in deinen Mails nach...")

	return {"error": False}, 200

@api.route("/user/password_reset/<int:id>/<string:token>", methods=["GET", "POST"])
def api_user_password_reset_with_token(id, token):
	user = User.query.get(id)
	if not user:
		return CommonErrors.INVALID_ID

	if not user.pw_reset_token:
		flash("Du kannst diesen Link nicht mehr benutzen.", "warning")
		return redirect("/")

	if token == user.pw_reset_token:
		user.pw_reset_token = None
		db.session.commit()
		login_user(user)
		flash("Du kannst dein Passwort jetzt in deinen Profil-Einstellungen ändern.", "info")
		return redirect("/profile")

	flash("Dieser Link ist nicht gültig.", "warning")
	return redirect("/")


@api.route("/user/<int:id>/delete", methods=["GET", "POST"])
@json_out
@authenticated
def api_user_delete(id):
	user = User.query.get(id)
	if not user:
		return CommonErrors.INVALID_ID

	if not current_user.can_access(user):
		return CommonErrors.NO_ACCESS

	Activity("User " + user.name + " von " + current_user.name + " geloescht!")
	user.delete()
	return {"error": False}

@api.route("/langs")
@json_out
def api_langs():
	return [l.info() for l in Lang.query.all()]

@api.route("/gametypes")
@json_out
def api_gametypes():
	return [gt.info() for gt in GameType.query.all()]

@api.route("/activate/<int:id>/<string:uuid>", methods=["GET", "POST"])
@json_out
def activate(id, uuid):
	user = User.query.get(id)

	if not user:
		return CommonErrors.INVALID_ID

	if user.validate(uuid):
		Activity(user.name + " hat sich erfolgreich validiert.")
		flash("Dein Account ist jetzt aktiviert.", "info")
		login_user(user)
		return redirect("/")

	return CommonErrors.BAD_REQUEST


@api.route("/login", methods=['POST'])
@json_out
def api_login():
	if request.mimetype == "application/json":
		if not request.json:
			return {"error": "Not valid JSON."}, 400
		email = request.json.get("email", None)
		password = request.json.get("password", None)
		remember = request.json.get("remember", False)
	elif request.mimetype == "application/x-www-form-urlencoded":
		email = request.form.get('email')
		password = request.form.get('password')
		remember = request.form.get('remember', False)
		if remember:
			remember = True
	else:
		return {'error': "Wrong Content-Type, must be application/json or application/x-www-form-urlencoded"}, 400
	if not email or not password:
		return {'error': 'Missing email or password'}, 400

	user = User.query.filter(User.email.ilike(email)).first()

	if not user:
		return {'error': 'Invalid email.'}, 404

	if not user.validated:
		return {'error': 'Account not activated.'}, 400

	if not user.check_pw(password):
		return {'error': 'Wrong password.'}, 400

	login_user(user, remember=remember)
	flash("Du hast dich eingeloggt.", "positive")

	Activity(user.name + " hat sich erfolgreich eingeloggt.")

	return {'error': False}

@api.route("/logout", methods=["GET", "POST"])
@json_out
def api_logout():
	if not current_user.is_authenticated():
		return {'error': 'Not logged in.'}
	logout_user()
	return {'error': False}

@api.route("/loggedin", methods=['GET', 'POST'])
@json_out
@authenticated
def api_logged_in():
	return current_user.info()

@api.route("/user/create", methods=['GET', 'POST'])
@json_out
def api_user_create():
	if request.mimetype == "application/json":
		if not request.json:
			return {"error": "Not valid JSON."}, 400
		username = request.json.get("username", None)
		lastname = request.json.get("lastname", None)
		firstname = request.json.get("firstname", None)
		password = request.json.get("password", None)
		email = request.json.get("email", None)
	elif request.mimetype == "application/x-www-form-urlencoded":
		username = request.form['username']
		lastname = request.form['lastname']
		firstname = request.form['firstname']
		password = request.form['password']
		email = request.form.get("email", None)
	else:
		return {"error": "Wrong Content-Type, must be application/json or application/x-www-form-urlencoded"}, 400
	if not username or not password or not email:
		return {'error': 'Missing username, password or email'}, 400

	if len(username) > 40:
		return {'error': 'Username too long'}, 400
	if len(password) < 3:
		return {'error': 'Password too short'}, 400
	
	try:
		User.query.filter(User.name.ilike(username)).one()
		return {'error': 'Username already registered'}, 400
	except NoResultFound:
		pass

	try:
		User.query.filter(User.email.ilike(email)).one()
		return {'error': 'EMail already registered'}, 400
	except NoResultFound:
		pass

	user = User(name=username, firstname=firstname, lastname=lastname, email=email)
	user.set_pw(password)

	db.session.add(user)
	# es muss zur Datenbank geschrieben werden, um die Infos zu bekommen
	db.session.commit()

	if not user.send_validation_mail():
		db.session.rollback()
		return {"error": "Invalid EMail."}, 400

	flash("Guck in deinen E-Mails nach einer Aktivierungsmail.", "info")

	return {'error': False, 'user': user.info()}, 200


@api.route("/ai/create")
@json_out
@authenticated
def api_ai_create():
	name = request.args.get('name', 'unbenannte ki')
	if len(name) == 0:
		name = 'unbenannte ki'
	desc = request.args.get('desc', 'unbeschriebene ki')
	## lang fix setzen

	lang = request.args.get('lang')
	if not lang:
		return {"error": "Language not specified."}, 400

	lang = Lang.query.get(lang)

	if not lang:
		return {'error': 'Invalid Language'}, 404

	type = request.args.get('type')
	if type:
		type = GameType.query.get(type)
		if not type:
			return {"error": "Invalid type."}, 400
	else:
		type = GameType.selected()

	ai = AI(name=name, user=current_user, desc=desc, lang=lang, type=type)
	db.session.add(ai)
	# es muss zur Datenbank geschrieben werden, um die ID zu bekommen
	db.session.commit()
	return {'error': False, 'ai': ai.info()}

@api.route("/ai/<int:id>/icon", methods=["GET"])
@cache.memoize(timeout=env.cache_max_age)
def ai_icon(id):
	ai = AI.query.get(id)
	if ai:
		return ai.icon()
	else:
		abort(404)

def upload_single_file(request, path, image=False):
	#print(request.mimetype)
	#print(request.files)
	#print(request.data)
	if request.mimetype == "multipart/form-data":
		if len(request.files) != 1:
			return {"error": "Invalid number of files attached."}, 400
		content = list(request.files.values())[0].read()
	else:
		content = request.data

	if image:
		mime = magic.from_buffer(content, mime=True).decode("ascii")
		print(mime, magic.from_buffer(content))
		if not "image/" in mime:
			## no gifs?
			return {"error": "Invalid mimetype for an image.", "mimetype": mime}, 400

	@ftp.safe
	def f():
		with ftp.ftp_host.open(path, "wb") as f:
			f.write(content)
		return {"error": False}, 200
	try:
		return f()
	except ftp.err:
		return CommonErrors.FTP_ERROR

@api.route("/ai/<int:id>/upload_icon", methods=["POST"])
@json_out
@authenticated
def api_ai_upload_icon(id):
	ai = AI.query.get(id)
	if ai:
		cache.delete_memoized(api_ai_icon, id)
		return upload_single_file(request, "AIs/"+str(id)+"/icon.png", image=True)
	else:
		return CommonErrors.INVALID_ID

@api.route("/ai/<int:id>/reset_icon", methods=["POST"])
@json_out
@authenticated
def api_ai_reset_icon(id):
	ai = AI.query.get(id)
	if ai:
		cache.delete_memoized(api_ai_icon, id)
		@ftp.safe
		def f():
			path = "AIs/"+str(id)+"/icon.png"
			if not ftp.ftp_host.path.isfile(path):
				return {"error": "No custom Icon"}, 400
			ftp.ftp_host.remove(path)
			return {"error": False}, 200

		try:
			return f()
		except ftp.err:
			return CommonErrors.FTP_ERROR
	else:
		return CommonErrors.INVALID_ID

@api.route("/user/<int:id>/upload_icon", methods=["POST"])
@json_out
@authenticated
def api_user_upload_icon(id):
	user = User.query.get(id)
	if user:
		cache.delete_memoized(api_user_icon, id)
		return upload_single_file(request, "Users/"+str(id)+"/icon.png", image=True)
	else:
		return CommonErrors.INVALID_ID


@api.route("/ai/<int:id>/update", methods=["POST"])
@json_out
@authenticated
def api_ai_update(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID

	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	a = Activity("AI " + ai.name + " geaendert")
	a.extratext = str(ai) + " -> "

	ai.set_name(request.form.get('name', ai.name))
	ai.desc = request.form.get('description', ai.desc)
	if 'lang' in request.form:
		l = Lang.query.get(request.form.get('lang'))
		if l:
			## remove versions, prompt user?
			ai.lang = l
			#for version in ai.version_list:
			#	version.delete()

	if 'extra[]' in request.form:
		extras = request.form.getlist("extra[]")
		ai.latest_version().extras(extras)

	# es muss zur Datenbank geschrieben werden, um die aktuellen Infos zu bekommen
	db.session.commit()

	a.extratext += str(ai)

	ai.updated()

	return ai.info()

@api.route("/ai/<int:id>/copy_example_code", methods=["GET", "POST"])
@json_out
@authenticated
def api_ai_copy_example_code(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	try:
		ai.ftp_sync()
	except ftp.err:
		return CommonErrors.FTP_ERROR


	if not ai.copy_example_code():
		return CommonErrors.FTP_ERROR

	return ({"error": False}, 200)


@api.route("/ai/<int:id>/delete", methods=["GET", "POST"])
@json_out
@authenticated
def api_ai_delete(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID

	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	Activity("AI " + ai.name + " von " + current_user.name + " geloescht!")
	ai.delete()
	return {"error": False}


@api.route("/ai/<int:id>/compile", methods=["GET"])
@authenticated
@rate_limited
@sse_stream
def api_ai_compile(id):
	ai = AI.query.get(id)
	if not ai:
		return (CommonErrors.INVALID_ID[0]["error"], "error")
	if not current_user.can_access(ai):
		return (CommonErrors.NO_ACCESS[0]["error"], "error")


	if ai.latest_version().frozen:
		return {"error": "AI_Version is frozen"}, 400
	ai.latest_version().compiled = True
	ai.latest_version().qualified = False
	db.session.commit()

	yield from backend.compile(ai)

@api.route("/ai/<int:id>/compile_blocking", methods=["GET"])
@authenticated
@json_out
def api_ai_compile_blocking(id):
	ai = AI.query.get(id)
	if not ai:
		return (CommonErrors.INVALID_ID[0]["error"], "error")
	if not current_user.can_access(ai):
		return (CommonErrors.NO_ACCESS[0]["error"], "error")

	if ai.latest_version().frozen:
		return {"error": "AI_Version is frozen"}, 400
	ai.latest_version().compiled = True
	ai.latest_version().qualified = False
	db.session.commit()

	compile_log = ""

	for data, event in backend.compile(ai):
		if event == "log":
			compile_log += data
		if event == "error":
			return {"error": data, "compilelog": compile_log}

	return {"error": None, "compilelog": compile_log}, 200


@api.route("/ai/<int:id>/qualify", methods=["GET"])
@authenticated
@rate_limited
@sse_stream
def ai_qualify(id):
	ai = AI.query.get(id)
	if not ai:
		return (CommonErrors.INVALID_ID[0]["error"], "error")
	if not current_user.can_access(ai):
		return (CommonErrors.NO_ACCESS[0]["error"], "error")


	if not ai.latest_version().compiled:
		return {"error": "AI_Version isnt compiled."}, 400
	if ai.latest_version().frozen:
		return {"error": "AI_Version is frozen."}, 400

	reqid = backend.request_qualify(ai)
	for data, event in backend.inprogress_log(reqid):
		if event == "state":
			yield json.dumps(data), event
		elif event == "success":
			yield "", "qualified"
			ai.latest_version().compiled = True
			ai.latest_version().qualified = True
			db.session.commit()

@api.route("/ai/<int:id>/freeze", methods=["POST"])
@json_out
@authenticated
def ai_freeze(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	for version in ai.version_list:
		if version.compiled and version.qualified:
			version.frozen = True

	db.session.commit()
	return {"error": False}, 200

@api.route("/ai/<int:id>/new_version", methods=["POST"])
@json_out
@authenticated
def ai_new_version(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	if any([not v.frozen for v in ai.version_list]):
		return {"error": "You need to freeze all prior versions to create a new one."}, 400
	ai.new_version()
	return {"error": False}, 200

@api.route("/ai/<int:id>/new_version_from_zip", methods=["POST"])
@json_out
@authenticated
def ai_new_version_from_zip(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	if any([not v.frozen for v in ai.version_list]):
		return {"error": "You need to freeze all prior versions to create a new one."}, 400
	ai.new_version()



	tmpdir = tempfile.mkdtemp()
	_, tmpzip = tempfile.mkstemp()
	with open(tmpzip, "wb") as f:
		f.write(request.data)

	try:
		with zipfile.ZipFile(tmpzip) as z:
			z.extractall(tmpdir)
	except zipfile.BadZipFile:
		return {"error": "Bad zip file."}, 400

	if not ftp.upload_tree(tmpdir, "AIs/{}/v{}".format(ai.id, ai.latest_version().version_id)):
		return CommonErrors.FTP_ERROR

	return {"error": False}, 200


@api.route("/ai/<int:id>/upload", methods=["POST"])
@json_out
@authenticated
def ai_upload(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	if not ('path' in request.form and 'filename' in request.form and 'data' in request.form):
		return CommonErrors.BAD_REQUEST

	path = request.form['path']
	if path.startswith("/") or ".." in path:
		return CommonErrors.BAD_REQUEST
	if not path.endswith("/"):
		path += "/"
	path = "AIs/{}/v{}/{}".format(id, ai.latest_version().version_id, path)
	filename = secure_filename(request.form['filename'])
	if not len(filename):
		return ({"error": "Missing filename."}, 400)
	data = request.form["data"]

	if ai.latest_version().frozen:
		return {"error": "AI is frozen, you need to create a new version."}, 400

	@ftp.safe
	def f():
		if not ftp.ftp_host.path.isdir(path):
			return ({'error': 'Invalid path.'}, 400)

		with open("tmpfile", "w") as f:
			f.write(data)

		ftp.ftp_host.upload("tmpfile", path + filename)
		return ({"error": False}, 200)

	try:
		return f()
	except ftp.err:
		return CommonErrors.FTP_ERROR



@api.route("/ai/<int:id>/delete_file", methods=["POST"])
@json_out
@authenticated
def ai_delete_file(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	if not ('path' in request.form and 'filename' in request.form):
		return CommonErrors.BAD_REQUEST

	path = request.form['path']
	if path.startswith("/") or ".." in path:
		return CommonErrors.BAD_REQUEST
	if not path.endswith("/"):
		path += "/"
	path = "AIs/{}/v{}/{}".format(id, ai.latest_version().version_id, path)
	filename = secure_filename(request.form['filename'])

	@ftp.safe
	def f():
		if not ftp.ftp_host.path.isdir(path):
			return ({'error': 'Invalid path.'}, 400)

		if ftp.ftp_host.path.isfile(path+filename):
			ftp.ftp_host.remove(path + filename)
		else:
			ftp.ftp_host.rmtree(path+filename)
		return ({"error": False}, 200)

	try:
		return f()
	except ftp.err:
		return CommonErrors.FTP_ERROR

@api.route("/ai/<int:id>/create_folder", methods=["POST"])
@json_out
@authenticated
def ai_create_folder(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS

	if not ('path' in request.form and 'name' in request.form):
		return CommonErrors.BAD_REQUEST

	path = request.form['path']
	if path.startswith("/") or ".." in path:
		return CommonErrors.BAD_REQUEST
	if not path.endswith("/"):
		path += "/"
	path = "AIs/{}/v{}/{}".format(id, ai.latest_version().version_id, path)
	name = secure_filename(request.form['name'])
	if not len(name):
		return ({"error": "Missing name."}, 400)

	@ftp.safe
	def f():
		if not ftp.ftp_host.path.isdir(path):
			return ({'error': 'Invalid path.'}, 400)

		ftp.ftp_host.mkdir(path + name)
		return ({"error": False}, 200)

	try:
		return f()
	except ftp.err:
		return CommonErrors.FTP_ERROR


@api.route("/ai/<int:id>/upload_zip", methods=["POST"])
@json_out
@authenticated
def ai_upload_zip(id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS
	return CommonErrors.NOT_IMPLEMENTED

@api.route("/ai/<int:id>/<int:version_id>/download_zip")
@authenticated
def ai_download_zip(id, version_id):
	ai = AI.query.get(id)
	if not ai:
		return CommonErrors.INVALID_ID
	if not current_user.can_access(ai):
		return CommonErrors.NO_ACCESS
	return CommonErrors.NOT_IMPLEMENTED


@api.route("/games/start", methods=["POST"])
@json_out
@authenticated
@rate_limited
def start_game():
	if not 'ai[]' in request.form:
		return CommonErrors.INVALID_ID

	ais = request.form.getlist("ai[]")
	print(ais)
	for i1, ai1 in enumerate(ais):
		for i2, ai2 in enumerate(ais):
			if i1 != i2 and ai1 == ai2:
				print("Nen Gegen die selben KIs")
				print(ais)
				return {"error": "No duplicate AIs allowed."}, 400

	ais = [AI.query.get(ai) for ai in ais]
	print(ais)
	if not all(ais):
		return CommonErrors.INVALID_ID

	if not any([current_user.can_access(ai) for ai in ais]):
		return CommonErrors.NO_ACCESS

	if not all([ai.latest_qualified_version() for ai in ais]):
		return {"error": "All AIs have to be qualified"}, 400

	backend.request_game(ais)

	return {"error": False}




@api.route("/admin/ftp_sync")
@json_out
@admin_required
def admin_ftp_sync():
	Activity(current_user.name + " hat FTP-Sync ausgelöst.")
	for ai in AI.query.all():
		try:
			ai.updated()
		except ftp.err:
			print("failed to Sync " + ai.name)
	return {"error": False}

@api.route("/admin/clear_db")
@json_out
@admin_required
def admin_clear_db():
	Activity(current_user.name + " hat Datenbanklöschung ausgelöst.")
	db.drop_all()
	populate()
	return {"error": False}


# #github-bequemlichkeit
# @api.route("/gh-webhook", methods=["POST"])
# @json_out
# def gh_webhook(*args, **kwargs):
# 	print("gh-webhook triggered")
# 	print(*args, **kwargs)
# 	func = request.environ.get('werkzeug.server.shutdown')
# 	if func is None:
# 		raise RuntimeError('Not running with the Werkzeug Server')
# 	func()
# 	return {"error": False}, 200

@api.route("/game_list_sse", methods=["GET"])
@sse_stream
def game_list_sse():
	yield ("connected", "connected")
	q = backend.subscribe_game_update()
	while True:
		try:
			update = q.get(timeout=15)
			d = backend.request(update["requestid"])

			if "status" in update:
				if update["status"] == "processed":
					render_inprogress_game = get_template_attribute("game_list.html", "render_inprogress_game")
					r = backend.request(update["requestid"])
					html = render_inprogress_game(
						dict(
							id=r["requestid"],
							ai0=r["ai0"],
							ai1=r["ai1"],
							status="angefangen...",
							inqueue=r["status"] == "processed"
						)
					)
					yield (html, "new_game")
			if "update" in update:
				yield (json.dumps({
					"id": update["requestid"],
					"status": "## Schritt "+str(update["update"])
				}), "update")

			if "finished_game_obj" in d:
				yield (json.dumps({
					"url": url_for("anonymous.game", id=d["finished_game_obj"].id),
					"id": d["requestid"],
					"status": repr(d["finished_game_obj"])
				}), "finished_game")
		except Empty:
			# falls es keine Verbindung mehr gibt wird der Generator hier beendet.
			yield None
	yield ("", "new_game")
	yield ("""{"id": 1, "status": "1/42"}""", "update")


@api.route("/upload_game_libs/<int:id>/<string:lang>", methods=["POST"])
@json_out
@admin_required
def upload_game_libs(id, lang):
	## id und lang gegen eigene Datenbank prüfen?

	@ftp.safe
	def f():
		if not ftp.ftp_host.path.isdir("Games/{}/{}/ailib".format(id, lang)):
			## ordner erstellen, falls jemand zeugs für nen neues spiel hochladen will
			return {"error": "Invalid GameID or Lang"}, 400
		if not "X-FileName" in request.headers:
			return {"error": "Missing filename"}, 400
		filename = request.headers.get("X-FileName")
		with ftp.ftp_host.open("Games/{}/{}/ailib/{}".format(id, lang, filename), "wb") as f:
			f.write(request.data)
		return {"error": False}, 200

	try:
		return f()
	except ftp.err:
		print("FTP ERR @ upload_game_libs")
		return CommonErrors.FTP_ERROR

@api.route("/upload_game_logic/<int:id>", methods=["POST"])
@json_out
@admin_required
def upload_game_logic(id):
	return upload_single_file(request, "Games/"+secure_filename(str(id))+"/Logic.jar")

@api.route("/gamelogic/<int:id>")
def game_logic(id):
	@ftp.failsafe_locked
	def f():
		if ftp.ftp_host.path.isfile("Games/"+secure_filename(str(id))+"/Logic.jar"):
			return ftp.send_file("Games/"+secure_filename(str(id))+"/Logic.jar")
		else:
			abort(503)
	return f()

@api.route("/lib/<string:lang>/<string:name>")
def lib_by_name(lang, name):
	## ratelimit
	return CommonErrors.NOT_IMPLEMENTED

@api.route("/data_container/<int:game_id>")
def data_container(game_id):
	p = "Games/{}/data_container.zip".format(secure_filename(str(game_id)))
	@ftp.failsafe_locked
	def f():
		if ftp.ftp_host.path.isfile(p):
			return ftp.send_file(p)
		else:
			abort(503)
	return f()

@api.route("/make_data_container/<int:game_id>")
@json_out
@admin_required
def make_data_container(game_id):
	from cli import _make_data_container
	if not GameType.query.get(game_id):
		return CommonErrors.INVALID_ID
	_make_data_container(str(game_id))
	return {"error": False}, 200


@api.route("/upload_codr", methods=["POST"])
@json_out
@admin_required
def upload_codr():
	return upload_single_file(request, "Data/codr.jar")

@api.route("/download_codr")
def download_codr():
	p = "Data/codr.jar"
	@ftp.failsafe_locked
	def f():
		if ftp.ftp_host.path.isfile(p):
			return ftp.send_file(p)
		else:
			abort(503)
	return f()


