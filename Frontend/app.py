import arrow
print("\n"*2 + "-"*36)
print("Turnierserver - Frontend - ", arrow.utcnow().to('local').format("HH:mm:ss"))
print("-"*36 + "\n"*2)

from flask import Flask, got_request_exception
from flask.ext.sqlalchemy import SQLAlchemy
from commons import cache


from api import api, login_manager
from anonymous import anonymous
from profile import profile
from database import db, populate, AI
from backend import backend
from _cfg import env
from activityfeed import activity_feed, Activity
from errorhandling import handle_errors
from time import time




app = Flask("Turnierserver - Frontend")
app.config.from_object("_cfg.env")
login_manager.init_app(app)

app.jinja_env.add_extension("jinja2.ext.do")

app.register_blueprint(api)
app.register_blueprint(anonymous)
app.register_blueprint(profile)
handle_errors(app)

if env.airbrake:
	#airbrake.io
	print("Initializing Airbrake")
	import airbrake
	airbrake_logger = airbrake.getLogger(api_key=env.airbrake_key, project_id=env.airbrake_id)
	def log_exception(sender, exception, **extra):
		airbrake_logger.exception(exception, extra=extra)
	got_request_exception.connect(log_exception, app)


print("Connecting to", env.db_url)
db.init_app(app)

if env.clean_db:
	with app.app_context():
		db.drop_all()
		populate(5)

cache.init_app(app)


Activity("Serverstart abgeschlossen...", extratext="Hier gehts los.\nAlle vorherigen Events sollten nicht wichtig sein.")


app.run(host="::", port=env.web_port, debug=env.debug, threaded=True)