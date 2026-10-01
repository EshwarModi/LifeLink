from flask import Blueprint, render_template, request, redirect, jsonify
from extensions import limiter
from services import auth_service

auth_bp = Blueprint('auth', __name__)


@auth_bp.route('/register', methods=['GET', 'POST'], endpoint='register')
@limiter.limit("10 per hour", methods=["POST"])
def register():
    if request.method == 'POST':
        data = request.get_json() if request.is_json else request.form.to_dict()
        res, status = auth_service.register_user(data)
        return jsonify(res), status
    return render_template('register.html')


@auth_bp.route('/login', methods=['GET', 'POST'], endpoint='login')
@limiter.limit("5 per minute", methods=["POST"])
def login():
    if request.method == 'POST':
        data = request.get_json() if request.is_json else request.form.to_dict()
        res, status = auth_service.login_user(data)
        return jsonify(res), status
    return render_template('login.html')


@auth_bp.route('/logout', endpoint='logout')
def logout():
    redirect_url = auth_service.logout_user()
    return redirect(redirect_url)
