import os
from flask import Flask, jsonify, render_template, request
from dotenv import load_dotenv
from flask_limiter.errors import RateLimitExceeded

from extensions import db, migrate, csrf, limiter

load_dotenv()


def create_app(config_object=None):
    app = Flask(__name__)

    instance_path = os.path.join(app.root_path, 'instance')
    os.makedirs(instance_path, exist_ok=True)
    default_db_path = os.path.join(instance_path, 'lifelink.db').replace('\\', '/')

    app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get('DATABASE_URL', f'sqlite:///{default_db_path}')
    app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY') or os.environ.get('FLASK_SECRET_KEY') or 'dev-secret-for-local'
    app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

    FLASK_ENV = os.environ.get('FLASK_ENV', 'development').lower()
    IS_PRODUCTION = FLASK_ENV == 'production'

    app.config['SESSION_COOKIE_HTTPONLY'] = True
    app.config['SESSION_COOKIE_SAMESITE'] = os.environ.get('SESSION_COOKIE_SAMESITE', 'Lax')
    app.config['SESSION_COOKIE_SECURE'] = os.environ.get('SESSION_COOKIE_SECURE', 'False').lower() in ('1', 'true', 'yes')

    if IS_PRODUCTION and app.config['SECRET_KEY'] in ('dev-secret-for-local', 'your-secret-key-change-this'):
        raise RuntimeError('SECRET_KEY must be set in environment for production deployments')

    if config_object:
        app.config.update(config_object)

    # Initialize extensions
    db.init_app(app)
    migrate.init_app(app, db)
    csrf.init_app(app)
    limiter.init_app(app)

    # Register error handlers
    @app.errorhandler(RateLimitExceeded)
    def ratelimit_handler(e):
        return jsonify({'error': f'Rate limit exceeded. {e.description}'}), 429

    @app.errorhandler(404)
    def not_found(e):
        if request.accept_mimetypes.accept_json and not request.accept_mimetypes.accept_html:
            return jsonify({'error': 'Not Found'}), 404
        return render_template('404.html'), 404

    @app.errorhandler(500)
    def server_error(e):
        return jsonify({'error': 'Internal Server Error'}), 500

    # Register blueprints
    from blueprints.auth import auth_bp
    from blueprints.dashboard import dashboard_bp
    from blueprints.requests import requests_bp
    from blueprints.api import api_bp

    app.register_blueprint(auth_bp)
    app.register_blueprint(dashboard_bp)
    app.register_blueprint(requests_bp)
    app.register_blueprint(api_bp)

    # Alias blueprint endpoints to root names so url_for('login'), url_for('dashboard'), etc. work seamlessly
    for endpoint, rules in list(app.url_map._rules_by_endpoint.items()):
        if '.' in endpoint:
            short_name = endpoint.split('.', 1)[1]
            if short_name not in app.url_map._rules_by_endpoint:
                app.url_map._rules_by_endpoint[short_name] = rules
            if short_name not in app.view_functions:
                app.view_functions[short_name] = app.view_functions[endpoint]

    return app


app = create_app()

if __name__ == '__main__':
    with app.app_context():
        db.create_all()

    host  = os.environ.get('APP_HOST', '0.0.0.0')
    port  = int(os.environ.get('APP_PORT', os.environ.get('PORT', 5000)))
    debug = os.environ.get('FLASK_DEBUG', 'False').lower() in ('1', 'true', 'yes')
    app.run(host=host, port=port, debug=debug)
