
class LocalEnvironment:
	db_url = "sqlite:///test.db"
	secret_key = "foobar"
	airbrake = False
	airbrake_key = None
	airbrake_id = None
	ftp_url = "127.0.0.1"
	ftp_uname = "user"
	ftp_pw = "pw"
	backend_port = 1333
	backend_url = "127.0.0.1"
	cache_max_age = 60
	web_port = 5000

env = LocalEnvironment