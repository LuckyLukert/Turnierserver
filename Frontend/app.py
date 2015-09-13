import arrow
from flask import Flask, got_request_exception
from werkzeug.contrib.fixers import ProxyFix
from flask.ext.login import current_user
from flask.ext.script import Manager
from flask.ext.migrate import Migrate, MigrateCommand

from commons import cache
from logger import logger

logger.info("Starte Frontend")

from api import api, login_manager
from views.anonymous import anonymous_blueprint
from views.authenticated import authenticated_blueprint
from database import db, refresh_session, GameType
from backend import backend
from _cfg import env
from errorhandling import handle_errors
from cli import manage

import time
import json


app = Flask("Turnierserver - Frontend")
app.config.from_object("_cfg.env")
app.url_map.strict_slashes = False
login_manager.init_app(app)

db_uri = env.SQLALCHEMY_DATABASE_URI.split("@")
db_uri[0] = ":".join(db_uri[0].split(":")[:2] + ["******"])
db_uri = "@".join(db_uri)
logger.info("Connecting to " + db_uri)
db.init_app(app)

migrate = Migrate(app, db)

manager = Manager(app)
manager.add_command('db', MigrateCommand)

app.jinja_env.filters["escapejs"] = lambda val: json.dumps(val)
app.jinja_env.add_extension("jinja2.ext.do")

app.register_blueprint(api)
app.register_blueprint(anonymous_blueprint)
app.register_blueprint(authenticated_blueprint)
handle_errors(app)

if env.airbrake:
	with open("../.git/refs/heads/master", "r") as f: head = f.read()

	logger.info("Initializing Airbrake")
	import airbrake
	airbrake_logger = airbrake.getLogger(api_key=env.airbrake_key, project_id=env.airbrake_id)
	def log_exception(sender, exception, **extra):
		extra["commit"] = head
		airbrake_logger.exception(exception, extra=extra)
	got_request_exception.connect(log_exception, app)

if True:
	# fix fuer Gunicorn und NGINX
	app.wsgi_app = ProxyFix(app.wsgi_app)


cache.init_app(app)
backend.app = app

@app.context_processor
def inject_globals():
	logged_in = False
	if current_user:
		if current_user.is_authenticated:
			logged_in = True

	current_gametype = GameType.selected(None, latest_on_none=True)
	return dict(env=env, logged_in=logged_in, current_gametype=current_gametype,
	            latest_gametype=GameType.latest(), gametypes=GameType.query.all())


db_session_timeout = time.time()

@app.before_request
def refresh_db_session():
	global db_session_timeout
	if time.time() > db_session_timeout + 60:
		db_session_timeout = time.time()
		refresh_session()

@manager.command
def run():
	"Startet den Server."
	app_run_params = dict(host="::", port=env.web_port, threaded=True)
	if env.ssl:
		import ssl
		context = ssl.SSLContext(ssl.PROTOCOL_TLSv1_2)
		context.load_cert_chain('server.crt', 'server.key')
		app_run_params["ssl_context"] = context
	app.run(**app_run_params)

manage(manager, app)

logger.info("Module geladen")

if __name__ == '__main__':
	manager.run()
