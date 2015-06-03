from flask import Blueprint, render_template, abort, flash
from flask.ext.login import current_user
from database import AI, User, Game, GameType, Game_inprogress
from activityfeed import Activity
from backend import backend
import json

anonymous = Blueprint("anonymous", __name__)

@anonymous.route("/")
def index():
	return render_template("index.html")

@anonymous.route("/ai_list")
def ai_list():
	ais = AI.query.all()
	columns = [ais[i:i+3] for i in range(0, len(ais), 3)]
	return render_template("ai_list.html", columns=columns)

@anonymous.route("/ai/<int:id>")
def ai(id):
	ai = AI.query.get(id)
	if not ai:
		abort(404)
	return render_template("ai.html", ai=ai)

@anonymous.route("/user_list")
def user_list():
	return render_template("user_list.html", user_list=User.query.all())

@anonymous.route("/user/<int:id>")
def user(id):
	user = User.query.get(id)
	if not user:
		abort(404)
	return render_template("user.html", user=user)

@anonymous.route("/game_list")
def game_list():
	return render_template("game_list.html", game_list=Game.query.all(), in_progress_games=backend.inprogress_games())

@anonymous.route("/game/<int:id>")
def game(id):
	game = Game.query.get(id)
	if not game:
		abort(404)

	return render_template(game.type.viz, game=game)

@anonymous.route("/game/inprogress/<int:id>")
def inprogress_game(id):
	## inpgrogress type
	t = GameType.query.first()
	game = Game_inprogress()
	return render_template(game.type.viz, game=game, inprogress=True, ai0=game.ais[0], ai1=game.ais[1])
