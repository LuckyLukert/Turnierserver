from database import db
from flask.ext.login import current_user
from functools import wraps


def authenticated(f):
	@wraps(f)
	def wrapper(*args, **kwargs):
		if current_user:
			if current_user.is_authenticated():
				try:
					ret = f(*args, **kwargs)
					db.session.commit()
					print("commited", ret)
					return ret
				except:
					db.session.rollback()
					db.session.close()
					raise
		return CommonErrors.NO_ACCESS
	return wrapper