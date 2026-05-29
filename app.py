from flask import Flask, render_template, request, redirect, url_for, session, jsonify, flash
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta
import os
from dotenv import load_dotenv
from flask_migrate import Migrate

load_dotenv()

app = Flask(__name__)
instance_path = os.path.join(app.root_path, 'instance')
os.makedirs(instance_path, exist_ok=True)
default_db_path = os.path.join(instance_path, 'lifelink.db').replace('\\', '/')
app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get('DATABASE_URL', f'sqlite:///{default_db_path}')
app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY') or os.environ.get('FLASK_SECRET_KEY') or 'dev-secret-for-local'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = os.environ.get('SQLALCHEMY_TRACK_MODIFICATIONS', 'False').lower() in ('1', 'true', 'yes')

db = SQLAlchemy(app)
migrate = Migrate(app, db)

# Determine environment
FLASK_ENV = os.environ.get('FLASK_ENV', os.environ.get('ENV', 'development')).lower()
IS_PRODUCTION = FLASK_ENV == 'production'

# Session / cookie security defaults
app.config['SESSION_COOKIE_HTTPONLY'] = True
app.config['SESSION_COOKIE_SAMESITE'] = os.environ.get('SESSION_COOKIE_SAMESITE', 'Lax')
app.config['SESSION_COOKIE_SECURE'] = os.environ.get('SESSION_COOKIE_SECURE', 'False').lower() in ('1', 'true', 'yes')

# Fail fast in production if secret key is not set
if IS_PRODUCTION and (not app.config.get('SECRET_KEY') or app.config['SECRET_KEY'] in ('dev-secret-for-local', 'your-secret-key-change-this')):
    raise RuntimeError('SECRET_KEY must be set in environment for production deployments')


# ── Database Models ──────────────────────────────────────────────────────────

class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password = db.Column(db.String(200), nullable=False)
    user_type = db.Column(db.String(10), nullable=False)  # 'donor' or 'seeker'
    full_name = db.Column(db.String(120), nullable=False)
    phone = db.Column(db.String(20), nullable=False)
    city = db.Column(db.String(80), nullable=False)
    state = db.Column(db.String(80), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)


class DonorProfile(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    blood_group = db.Column(db.String(5), nullable=False)
    age = db.Column(db.Integer, nullable=False)
    gender = db.Column(db.String(10), nullable=False)
    last_donation_date = db.Column(db.DateTime)
    available_to_donate = db.Column(db.Boolean, default=True)
    medical_conditions = db.Column(db.Text)
    updated_at = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    user = db.relationship('User', backref='donor_profile')


class SeekerRequest(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    blood_group = db.Column(db.String(5), nullable=False)
    units_needed = db.Column(db.Integer, nullable=False)
    urgency = db.Column(db.String(20), nullable=False)  # 'urgent', 'normal'
    reason = db.Column(db.Text, nullable=False)
    hospital_name = db.Column(db.String(120), nullable=False)
    hospital_address = db.Column(db.Text, nullable=False)
    required_by = db.Column(db.DateTime, nullable=False)
    status = db.Column(db.String(20), default='open')  # 'open', 'fulfilled', 'cancelled'
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    user = db.relationship('User', backref='seeker_requests')


class Match(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    request_id = db.Column(db.Integer, db.ForeignKey('seeker_request.id'), nullable=False)
    donor_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    matched_at = db.Column(db.DateTime, default=datetime.utcnow)
    contact_shared = db.Column(db.Boolean, default=False)
    request = db.relationship('SeekerRequest', backref='matches')
    donor = db.relationship('User', backref='matches')


# ── Routes ───────────────────────────────────────────────────────────────────

@app.route('/')
def index():
    if 'user_id' in session:
        return redirect(url_for('dashboard'))
    return redirect(url_for('home'))


@app.route('/home')
def home():
    if 'user_id' in session:
        return redirect(url_for('dashboard'))
    donor_count = User.query.filter_by(user_type='donor').count()
    requests_count = SeekerRequest.query.filter_by(status='open').count()
    return render_template('home.html', donor_count=donor_count, requests_count=requests_count)


@app.route('/register', methods=['GET', 'POST'])
def register():
    if request.method == 'POST':
        data = request.get_json() if request.is_json else request.form

        if User.query.filter_by(username=data.get('username')).first():
            return jsonify({'error': 'Username already exists'}), 400

        if User.query.filter_by(email=data.get('email')).first():
            return jsonify({'error': 'Email already exists'}), 400

        user = User(
            username=data.get('username'),
            email=data.get('email'),
            password=generate_password_hash(data.get('password')),
            user_type=data.get('user_type'),
            full_name=data.get('full_name'),
            phone=data.get('phone'),
            city=data.get('city'),
            state=data.get('state')
        )

        db.session.add(user)
        db.session.commit()

        if data.get('user_type') == 'donor':
            donor_profile = DonorProfile(
                user_id=user.id,
                blood_group=data.get('blood_group'),
                age=int(data.get('age')),
                gender=data.get('gender')
            )
            db.session.add(donor_profile)
            db.session.commit()

        session['user_id'] = user.id
        session['user_type'] = user.user_type
        return jsonify({'success': True, 'redirect': url_for('dashboard')}), 200

    return render_template('register.html')


@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        data = request.get_json() if request.is_json else request.form
        user = User.query.filter_by(username=data.get('username')).first()

        if user and check_password_hash(user.password, data.get('password')):
            session['user_id'] = user.id
            session['user_type'] = user.user_type
            return jsonify({'success': True, 'redirect': url_for('dashboard')}), 200

        return jsonify({'error': 'Invalid credentials'}), 401

    return render_template('login.html')


@app.route('/logout')
def logout():
    session.clear()
    return redirect(url_for('home'))


@app.route('/dashboard')
def dashboard():
    if 'user_id' not in session:
        return redirect(url_for('login'))

    if session['user_type'] == 'donor':
        return redirect(url_for('donor_dashboard'))
    else:
        return redirect(url_for('seeker_dashboard'))


@app.route('/donor/dashboard')
def donor_dashboard():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return redirect(url_for('login'))

    user = db.session.get(User, session['user_id'])
    if user is None:
        session.clear()
        return redirect(url_for('login'))

    donor_profile = DonorProfile.query.filter_by(user_id=user.id).first()
    matches = Match.query.filter_by(donor_id=user.id).all()

    return render_template('donor.html', user=user, donor_profile=donor_profile, matches=matches)


@app.route('/seeker/dashboard')
def seeker_dashboard():
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return redirect(url_for('login'))

    user = db.session.get(User, session['user_id'])
    if user is None:
        session.clear()
        return redirect(url_for('login'))

    requests = SeekerRequest.query.filter_by(user_id=user.id).order_by(SeekerRequest.created_at.desc()).all()

    return render_template('seeker.html', user=user, requests=requests)


@app.route('/seeker/create-request', methods=['POST'])
def create_request():
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request data'}), 400

    try:
        required_by = datetime.fromisoformat(data.get('required_by'))
    except (ValueError, TypeError):
        return jsonify({'error': 'Invalid date format'}), 400

    seeker_request = SeekerRequest(
        user_id=session['user_id'],
        blood_group=data.get('blood_group'),
        units_needed=int(data.get('units_needed')),
        urgency=data.get('urgency'),
        reason=data.get('reason'),
        hospital_name=data.get('hospital_name'),
        hospital_address=data.get('hospital_address'),
        required_by=required_by
    )

    db.session.add(seeker_request)
    db.session.commit()

    # Find matching donors and create match records
    matching_donors = DonorProfile.query.filter_by(
        blood_group=data.get('blood_group'),
        available_to_donate=True
    ).all()

    for donor in matching_donors:
        match = Match(request_id=seeker_request.id, donor_id=donor.user_id)
        db.session.add(match)

    db.session.commit()
    return jsonify({'success': True, 'request_id': seeker_request.id}), 200


@app.route('/seeker/update-request/<int:request_id>', methods=['POST'])
def update_request_status(request_id):
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    new_status = data.get('status') if data else None

    if new_status not in ('fulfilled', 'cancelled'):
        return jsonify({'error': 'Invalid status'}), 400

    seeker_request = SeekerRequest.query.filter_by(
        id=request_id, user_id=session['user_id']
    ).first()

    if not seeker_request:
        return jsonify({'error': 'Request not found'}), 404

    if seeker_request.status != 'open':
        return jsonify({'error': 'Only open requests can be updated'}), 400

    seeker_request.status = new_status
    db.session.commit()
    return jsonify({'success': True}), 200


@app.route('/api/find-donors', methods=['GET'])
def find_donors():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401

    blood_group = request.args.get('blood_group')
    city = request.args.get('city')

    query = DonorProfile.query.filter_by(blood_group=blood_group, available_to_donate=True)

    if city:
        query = query.join(User).filter(User.city.ilike(f'%{city}%'))

    donors = query.all()
    return jsonify([{
        'id': d.id,
        'user': {
            'full_name': d.user.full_name,
            'city': d.user.city,
            'state': d.user.state,
            'phone': d.user.phone if session.get('user_type') == 'admin' else 'Hidden'
        },
        'blood_group': d.blood_group,
        'age': d.age
    } for d in donors])


@app.route('/api/find-requests', methods=['GET'])
def find_requests():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    donor_profile = DonorProfile.query.filter_by(user_id=session['user_id']).first()
    if not donor_profile:
        return jsonify({'error': 'Donor profile not found'}), 404

    open_requests = SeekerRequest.query.filter(
        SeekerRequest.blood_group == donor_profile.blood_group,
        SeekerRequest.status == 'open'
    ).order_by(SeekerRequest.urgency.desc(), SeekerRequest.required_by.asc()).all()

    return jsonify([{
        'id': r.id,
        'user': {
            'full_name': r.user.full_name,
            'city': r.user.city,
            'phone': r.user.phone
        },
        'blood_group': r.blood_group,
        'units_needed': r.units_needed,
        'urgency': r.urgency,
        'hospital_name': r.hospital_name,
        'required_by': r.required_by.isoformat()
    } for r in open_requests])


@app.route('/api/accept-match/<int:match_id>', methods=['POST'])
def accept_match(match_id):
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    match = db.session.get(Match, match_id)
    if not match or match.donor_id != session['user_id']:
        return jsonify({'error': 'Not found'}), 404

    match.contact_shared = True
    db.session.commit()
    return jsonify({'success': True})


@app.route('/api/donor/update-availability', methods=['POST'])
def update_donor_availability():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request data'}), 400

    donor_profile = DonorProfile.query.filter_by(user_id=session['user_id']).first()
    if not donor_profile:
        return jsonify({'error': 'Donor profile not found'}), 404

    if 'available_to_donate' in data:
        donor_profile.available_to_donate = bool(data['available_to_donate'])
    if 'last_donation_date' in data and data['last_donation_date']:
        try:
            donor_profile.last_donation_date = datetime.fromisoformat(data['last_donation_date'])
        except ValueError:
            return jsonify({'error': 'Invalid date format'}), 400
    if 'medical_conditions' in data:
        donor_profile.medical_conditions = data['medical_conditions']

    db.session.commit()
    return jsonify({'success': True})


@app.route('/health')
def health():
    try:
        db.session.execute(db.text('SELECT 1'))
        db_status = 'ok'
    except Exception:
        db_status = 'error'
    return jsonify({'status': 'ok', 'db': db_status}), 200


@app.errorhandler(404)
def not_found(e):
    if request.accept_mimetypes.accept_json and not request.accept_mimetypes.accept_html:
        return jsonify({'error': 'Not Found'}), 404
    return render_template('404.html'), 404


@app.errorhandler(500)
def server_error(e):
    return jsonify({'error': 'Internal Server Error'}), 500


if __name__ == '__main__':
    with app.app_context():
        db.create_all()

    host = os.environ.get('APP_HOST', '0.0.0.0')
    port = int(os.environ.get('APP_PORT', os.environ.get('PORT', 5000)))
    debug = os.environ.get('FLASK_DEBUG', 'False').lower() in ('1', 'true', 'yes')

    app.run(host=host, port=port, debug=debug)
