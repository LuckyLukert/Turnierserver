import logging
## TODO: logging wo anders hin verschieben
hellblau = "\033[36m"
level_strings = {
	logging.DEBUG: ("\033[1m", "DEBUG"),
	logging.INFO: ("\033[1m", "INFO"),
	logging.WARNING: ("\033[1;33m", "WARNING"),
	logging.ERROR: ("\033[1;31m", "ERROR"),
	logging.CRITICAL: ("\033[1;31m", "CRITICAL")
}
level_strings_len = max([len(level_strings[key][1]) for key in level_strings])
for key in level_strings:
	color, text = level_strings[key]
	text = "{:>{}}".format(text, level_strings_len)
	level_strings[key] = (color, text)

normal = "\033[0m"
in_str = "\033[0min\033[1;32m"
logger = logging.getLogger('Frontend')
logger.setLevel(logging.DEBUG)
fh = logging.FileHandler('frontend.log')
fh.setLevel(logging.DEBUG)
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)

class ColorfulFormatter(logging.Formatter):
	orig_format = hellblau + "[%(asctime)s]" + normal + " {}" + in_str + " %(funcName)s (%(filename)s:%(lineno)d)" + normal + " %(message)s"

	def format(self, record):
		s = level_strings.get(record.levelno, "\033[1;31mUNKNOWN_LEVEL("+str(record.levelno)+")")
		self._fmt = self.orig_format.format(s[0] + s[1] + " ")
		self._style = logging.PercentStyle(self._fmt)
		return logging.Formatter.format(self, record)

formatter = ColorfulFormatter()
formatter.datefmt = "%d.%m %H:%M:%S"
fh.setFormatter(formatter)
ch.setFormatter(formatter)

logger.addHandler(fh)
logger.addHandler(ch)


formatter = logging.Formatter(hellblau + "[%(asctime)s]" + normal + " WERKZEUG" + normal + " %(message)s")
formatter.datefmt = "%d.%m %H:%M:%S"
wl = logging.getLogger('werkzeug')
wl.setLevel(logging.DEBUG)
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
ch.setFormatter(formatter)
wl.addHandler(ch)


import werkzeug.serving
from werkzeug._internal import _log
WSGIRequestHandler = werkzeug.serving.WSGIRequestHandler

def log(self, type, message, *args):
	_log(type, '%s - %s\n' % (self.address_string(),
								message % args))
WSGIRequestHandler.log = log
werkzeug.serving.WSGIRequestHandler = WSGIRequestHandler


from database import db
from flask import abort
from flask.ext.login import current_user
from flask.ext.cache import Cache
from functools import wraps


class CommonErrors:
	BAD_REQUEST = ({'error': 'Bad request.'}, 400)
	INVALID_ID = ({'error': 'Invalid id.'}, 404)
	NO_ACCESS = ({'error': 'Insufficient permissions.'}, 401)
	IM_A_TEAPOT = ({'error': 'I\'m a teapot.'}, 418)
	NOT_IMPLEMENTED = ({'error': 'Not implemented.'}, 501)
	FTP_ERROR = ({'error': 'FTP-Error'}, 503)


def authenticated(f):
	@wraps(f)
	def wrapper(*args, **kwargs):
		if current_user:
			if current_user.is_authenticated():
				try:
					ret = f(*args, **kwargs)
					db.session.commit()
					return ret
				except:
					db.session.rollback()
					db.session.close()
					raise
		return CommonErrors.NO_ACCESS
	return wrapper


def authenticated_web(f):
	@wraps(f)
	def wrapper(*args, **kwargs):
		if current_user:
			if current_user.is_authenticated():
				try:
					ret = f(*args, **kwargs)
					db.session.commit()
					return ret
				except:
					db.session.rollback()
					raise
		return abort(401)
	return wrapper

cache = Cache(config={'CACHE_TYPE': 'simple'})
