from flask import session, url_for
from werkzeug.security import generate_password_hash, check_password_hash
from extensions import db
from models import User, DonorProfile
from validators import validate_register


def register_user(data):
    errors = validate_register(data)
    if errors:
        return {'error': errors[0]}, 400

    if User.query.filter_by(username=data['username'].strip()).first():
        return {'error': 'Username already exists'}, 400
    if User.query.filter_by(email=data['email'].strip().lower()).first():
        return {'error': 'Email already exists'}, 400

    user = User(
        username  = data['username'].strip(),
        email     = data['email'].strip().lower(),
        password  = generate_password_hash(data['password']),
        user_type = data['user_type'],
        full_name = data['full_name'].strip(),
        phone     = data['phone'].strip(),
        city      = data['city'].strip(),
        state     = data['state'].strip()
    )
    db.session.add(user)
    db.session.commit()

    if data['user_type'] == 'donor':
        dp = DonorProfile(
            user_id     = user.id,
            blood_group = data['blood_group'],
            age         = int(data['age']),
            gender      = data['gender']
        )
        db.session.add(dp)
        db.session.commit()

    session.clear()
    session['user_id']   = user.id
    session['user_type'] = user.user_type
    return {'success': True, 'redirect': url_for('dashboard')}, 200


def login_user(data):
    if not data.get('username') or not data.get('password'):
        return {'error': 'Username and password are required'}, 400

    user = User.query.filter_by(username=data['username'].strip()).first()
    if user and check_password_hash(user.password, data['password']):
        session.clear()
        session['user_id']   = user.id
        session['user_type'] = user.user_type
        return {'success': True, 'redirect': url_for('dashboard')}, 200

    return {'error': 'Invalid username or password'}, 401


def logout_user():
    session.clear()
    return url_for('home')
