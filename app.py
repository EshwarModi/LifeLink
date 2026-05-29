from flask import Flask, render_template, request, redirect, url_for, session, jsonify, flash
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta
import os
import re
from dotenv import load_dotenv
from flask_migrate import Migrate

load_dotenv()

app = Flask(__name__)
instance_path = os.path.join(app.root_path, 'instance')
os.makedirs(instance_path, exist_ok=True)
default_db_path = os.path.join(instance_path, 'lifelink.db').replace('\\', '/')
app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get('DATABASE_URL', f'sqlite:///{default_db_path}')
app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY') or os.environ.get('FLASK_SECRET_KEY') or 'dev-secret-for-local'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
migrate = Migrate(app, db)

FLASK_ENV = os.environ.get('FLASK_ENV', 'development').lower()
IS_PRODUCTION = FLASK_ENV == 'production'

app.config['SESSION_COOKIE_HTTPONLY'] = True
app.config['SESSION_COOKIE_SAMESITE'] = os.environ.get('SESSION_COOKIE_SAMESITE', 'Lax')
app.config['SESSION_COOKIE_SECURE'] = os.environ.get('SESSION_COOKIE_SECURE', 'False').lower() in ('1', 'true', 'yes')

if IS_PRODUCTION and app.config['SECRET_KEY'] in ('dev-secret-for-local', 'your-secret-key-change-this'):
    raise RuntimeError('SECRET_KEY must be set in environment for production deployments')

VALID_BLOOD_GROUPS = {'A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-'}
VALID_URGENCY     = {'urgent', 'normal'}
VALID_USER_TYPES  = {'donor', 'seeker'}
VALID_GENDERS     = {'Male', 'Female', 'Other'}


# ── Helpers ──────────────────────────────────────────────────────────────────

def validate_register(data):
    errors = []
    if not data.get('username') or len(data['username'].strip()) < 3:
        errors.append('Username must be at least 3 characters.')
    if not data.get('email') or not re.match(r'^[^@]+@[^@]+\.[^@]+$', data['email']):
        errors.append('A valid email is required.')
    if not data.get('password') or len(data['password']) < 6:
        errors.append('Password must be at least 6 characters.')
    if data.get('user_type') not in VALID_USER_TYPES:
        errors.append('Invalid user type.')
    if not data.get('full_name') or len(data['full_name'].strip()) < 2:
        errors.append('Full name is required.')
    if not data.get('phone') or not re.match(r'^\+?[\d\s\-]{7,20}$', data['phone']):
        errors.append('A valid phone number is required.')
    if not data.get('city') or len(data['city'].strip()) < 2:
        errors.append('City is required.')
    if not data.get('state') or len(data['state'].strip()) < 2:
        errors.append('State is required.')
    if data.get('user_type') == 'donor':
        if data.get('blood_group') not in VALID_BLOOD_GROUPS:
            errors.append('Invalid blood group.')
        try:
            age = int(data.get('age', 0))
            if age < 18 or age > 65:
                errors.append('Donor age must be between 18 and 65.')
        except (ValueError, TypeError):
            errors.append('Age must be a number.')
        if data.get('gender') not in VALID_GENDERS:
            errors.append('Invalid gender.')
    return errors


def validate_request(data):
    errors = []
    if data.get('blood_group') not in VALID_BLOOD_GROUPS:
        errors.append('Invalid blood group.')
    try:
        units = int(data.get('units_needed', 0))
        if units < 1 or units > 20:
            errors.append('Units needed must be between 1 and 20.')
    except (ValueError, TypeError):
        errors.append('Units needed must be a number.')
    if data.get('urgency') not in VALID_URGENCY:
        errors.append('Invalid urgency level.')
    if not data.get('reason') or len(data['reason'].strip()) < 5:
        errors.append('Reason is required.')
    if not data.get('hospital_name') or len(data['hospital_name'].strip()) < 2:
        errors.append('Hospital name is required.')
    if not data.get('hospital_address') or len(data['hospital_address'].strip()) < 5:
        errors.append('Hospital address is required.')
    if not data.get('required_by'):
        errors.append('Required by date is required.')
    return errors


# ── Models ───────────────────────────────────────────────────────────────────

class User(db.Model):
    id         = db.Column(db.Integer, primary_key=True)
    username   = db.Column(db.String(80), unique=True, nullable=False)
    email      = db.Column(db.String(120), unique=True, nullable=False)
    password   = db.Column(db.String(200), nullable=False)
    user_type  = db.Column(db.String(10), nullable=False)
    full_name  = db.Column(db.String(120), nullable=False)
    phone      = db.Column(db.String(20), nullable=False)
    city       = db.Column(db.String(80), nullable=False)
    state      = db.Column(db.String(80), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)


class DonorProfile(db.Model):
    id                 = db.Column(db.Integer, primary_key=True)
    user_id            = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    blood_group        = db.Column(db.String(5), nullable=False)
    age                = db.Column(db.Integer, nullable=False)
    gender             = db.Column(db.String(10), nullable=False)
    last_donation_date = db.Column(db.DateTime)
    available_to_donate = db.Column(db.Boolean, default=True)
    medical_conditions = db.Column(db.Text)
    updated_at         = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    user               = db.relationship('User', backref='donor_profile')


class SeekerRequest(db.Model):
    id               = db.Column(db.Integer, primary_key=True)
    user_id          = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    blood_group      = db.Column(db.String(5), nullable=False)
    units_needed     = db.Column(db.Integer, nullable=False)
    urgency          = db.Column(db.String(20), nullable=False)
    reason           = db.Column(db.Text, nullable=False)
    hospital_name    = db.Column(db.String(120), nullable=False)
    hospital_address = db.Column(db.Text, nullable=False)
    required_by      = db.Column(db.DateTime, nullable=False)
    status           = db.Column(db.String(20), default='open')
    created_at       = db.Column(db.DateTime, default=datetime.utcnow)
    user             = db.relationship('User', backref='seeker_requests')


class Match(db.Model):
    id             = db.Column(db.Integer, primary_key=True)
    request_id     = db.Column(db.Integer, db.ForeignKey('seeker_request.id'), nullable=False)
    donor_id       = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    matched_at     = db.Column(db.DateTime, default=datetime.utcnow)
    contact_shared = db.Column(db.Boolean, default=False)
    status         = db.Column(db.String(20), default='pending')  # pending, accepted, declined
    request        = db.relationship('SeekerRequest', backref='matches')
    donor          = db.relationship('User', backref='matches')


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
    donor_count    = User.query.filter_by(user_type='donor').count()
    requests_count = SeekerRequest.query.filter_by(status='open').count()
    return render_template('home.html', donor_count=donor_count, requests_count=requests_count)


@app.route('/register', methods=['GET', 'POST'])
def register():
    if request.method == 'POST':
        data = request.get_json() if request.is_json else request.form.to_dict()

        errors = validate_register(data)
        if errors:
            return jsonify({'error': errors[0]}), 400

        if User.query.filter_by(username=data['username'].strip()).first():
            return jsonify({'error': 'Username already exists'}), 400
        if User.query.filter_by(email=data['email'].strip().lower()).first():
            return jsonify({'error': 'Email already exists'}), 400

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

        session['user_id']   = user.id
        session['user_type'] = user.user_type
        return jsonify({'success': True, 'redirect': url_for('dashboard')}), 200

    return render_template('register.html')


@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        data = request.get_json() if request.is_json else request.form.to_dict()
        if not data.get('username') or not data.get('password'):
            return jsonify({'error': 'Username and password are required'}), 400

        user = User.query.filter_by(username=data['username'].strip()).first()
        if user and check_password_hash(user.password, data['password']):
            session['user_id']   = user.id
            session['user_type'] = user.user_type
            return jsonify({'success': True, 'redirect': url_for('dashboard')}), 200

        return jsonify({'error': 'Invalid username or password'}), 401

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
    return redirect(url_for('seeker_dashboard'))


@app.route('/donor/dashboard')
def donor_dashboard():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return redirect(url_for('login'))
    user = db.session.get(User, session['user_id'])
    if not user:
        session.clear()
        return redirect(url_for('login'))
    donor_profile = DonorProfile.query.filter_by(user_id=user.id).first()
    matches       = Match.query.filter_by(donor_id=user.id).all()
    return render_template('donor.html', user=user, donor_profile=donor_profile, matches=matches)


@app.route('/seeker/dashboard')
def seeker_dashboard():
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return redirect(url_for('login'))
    user = db.session.get(User, session['user_id'])
    if not user:
        session.clear()
        return redirect(url_for('login'))
    requests = SeekerRequest.query.filter_by(user_id=user.id)\
                                  .order_by(SeekerRequest.created_at.desc()).all()
    return render_template('seeker.html', user=user, requests=requests)


@app.route('/seeker/create-request', methods=['POST'])
def create_request():
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request data'}), 400

    errors = validate_request(data)
    if errors:
        return jsonify({'error': errors[0]}), 400

    try:
        required_by = datetime.fromisoformat(data['required_by'])
    except (ValueError, TypeError):
        return jsonify({'error': 'Invalid date format'}), 400

    if required_by <= datetime.utcnow():
        return jsonify({'error': 'Required by date must be in the future'}), 400

    sr = SeekerRequest(
        user_id          = session['user_id'],
        blood_group      = data['blood_group'],
        units_needed     = int(data['units_needed']),
        urgency          = data['urgency'],
        reason           = data['reason'].strip(),
        hospital_name    = data['hospital_name'].strip(),
        hospital_address = data['hospital_address'].strip(),
        required_by      = required_by
    )
    db.session.add(sr)
    db.session.commit()

    # Auto-match available donors with same blood group
    matching_donors = DonorProfile.query.filter_by(
        blood_group=data['blood_group'], available_to_donate=True
    ).all()
    for dp in matching_donors:
        db.session.add(Match(request_id=sr.id, donor_id=dp.user_id))
    db.session.commit()

    return jsonify({'success': True, 'request_id': sr.id}), 200


@app.route('/seeker/update-request/<int:request_id>', methods=['POST'])
def update_request_status(request_id):
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data       = request.get_json()
    new_status = data.get('status') if data else None
    if new_status not in ('fulfilled', 'cancelled'):
        return jsonify({'error': 'Invalid status'}), 400

    sr = SeekerRequest.query.filter_by(id=request_id, user_id=session['user_id']).first()
    if not sr:
        return jsonify({'error': 'Request not found'}), 404
    if sr.status != 'open':
        return jsonify({'error': 'Only open requests can be updated'}), 400

    sr.status = new_status
    db.session.commit()
    return jsonify({'success': True}), 200


@app.route('/seeker/edit-request/<int:request_id>', methods=['POST'])
def edit_request(request_id):
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request data'}), 400

    sr = SeekerRequest.query.filter_by(id=request_id, user_id=session['user_id']).first()
    if not sr:
        return jsonify({'error': 'Request not found'}), 404
    if sr.status != 'open':
        return jsonify({'error': 'Only open requests can be edited'}), 400

    errors = validate_request(data)
    if errors:
        return jsonify({'error': errors[0]}), 400

    try:
        required_by = datetime.fromisoformat(data['required_by'])
    except (ValueError, TypeError):
        return jsonify({'error': 'Invalid date format'}), 400

    sr.blood_group      = data['blood_group']
    sr.units_needed     = int(data['units_needed'])
    sr.urgency          = data['urgency']
    sr.reason           = data['reason'].strip()
    sr.hospital_name    = data['hospital_name'].strip()
    sr.hospital_address = data['hospital_address'].strip()
    sr.required_by      = required_by
    db.session.commit()
    return jsonify({'success': True}), 200


@app.route('/api/find-requests', methods=['GET'])
def find_requests():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    dp = DonorProfile.query.filter_by(user_id=session['user_id']).first()
    if not dp:
        return jsonify({'error': 'Donor profile not found'}), 404

    open_requests = SeekerRequest.query.filter(
        SeekerRequest.blood_group == dp.blood_group,
        SeekerRequest.status == 'open'
    ).order_by(SeekerRequest.urgency.desc(), SeekerRequest.required_by.asc()).all()

    return jsonify([{
        'id':           r.id,
        'blood_group':  r.blood_group,
        'units_needed': r.units_needed,
        'urgency':      r.urgency,
        'hospital_name': r.hospital_name,
        'required_by':  r.required_by.isoformat(),
        'user': {
            'full_name': r.user.full_name,
            'city':      r.user.city,
            # phone is intentionally omitted — only revealed after match accepted
        }
    } for r in open_requests])


@app.route('/api/donor/respond', methods=['POST'])
def donor_respond():
    """Donor clicks 'I Can Help' on a request — creates or updates a match record."""
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    data       = request.get_json()
    request_id = data.get('request_id') if data else None
    if not request_id:
        return jsonify({'error': 'request_id is required'}), 400

    sr = db.session.get(SeekerRequest, request_id)
    if not sr or sr.status != 'open':
        return jsonify({'error': 'Request not found or no longer open'}), 404

    # Check donor blood group matches
    dp = DonorProfile.query.filter_by(user_id=session['user_id']).first()
    if not dp or dp.blood_group != sr.blood_group:
        return jsonify({'error': 'Blood group mismatch'}), 400

    # Upsert match record
    match = Match.query.filter_by(request_id=request_id, donor_id=session['user_id']).first()
    if not match:
        match = Match(request_id=request_id, donor_id=session['user_id'])
        db.session.add(match)

    match.status         = 'accepted'
    match.contact_shared = True
    db.session.commit()

    # Return seeker contact now that donor has accepted
    return jsonify({
        'success':    True,
        'match_id':   match.id,
        'seeker_name':  sr.user.full_name,
        'seeker_phone': sr.user.phone,
        'hospital':     sr.hospital_name
    }), 200


@app.route('/api/accept-match/<int:match_id>', methods=['POST'])
def accept_match(match_id):
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    match = db.session.get(Match, match_id)
    if not match or match.donor_id != session['user_id']:
        return jsonify({'error': 'Not found'}), 404

    match.contact_shared = True
    match.status         = 'accepted'
    db.session.commit()
    return jsonify({'success': True, 'seeker_phone': match.request.user.phone})


@app.route('/api/donor/update-availability', methods=['POST'])
def update_donor_availability():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401

    data = request.get_json()
    if not data:
        return jsonify({'error': 'Invalid request data'}), 400

    dp = DonorProfile.query.filter_by(user_id=session['user_id']).first()
    if not dp:
        return jsonify({'error': 'Donor profile not found'}), 404

    if 'available_to_donate' in data:
        dp.available_to_donate = bool(data['available_to_donate'])
    if data.get('last_donation_date'):
        try:
            dp.last_donation_date = datetime.fromisoformat(data['last_donation_date'])
        except ValueError:
            return jsonify({'error': 'Invalid date format'}), 400
    if 'medical_conditions' in data:
        dp.medical_conditions = data['medical_conditions']

    db.session.commit()
    return jsonify({'success': True})


@app.route('/api/find-donors', methods=['GET'])
def find_donors():
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401

    blood_group = request.args.get('blood_group')
    city        = request.args.get('city')

    if blood_group not in VALID_BLOOD_GROUPS:
        return jsonify({'error': 'Invalid blood group'}), 400

    q = DonorProfile.query.filter_by(blood_group=blood_group, available_to_donate=True)
    if city:
        q = q.join(User).filter(User.city.ilike(f'%{city}%'))

    return jsonify([{
        'id':           d.id,
        'blood_group':  d.blood_group,
        'age':          d.age,
        'user': {
            'full_name': d.user.full_name,
            'city':      d.user.city,
            'state':     d.user.state,
            # phone hidden — only shared via match flow
        }
    } for d in q.all()])


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

    host  = os.environ.get('APP_HOST', '0.0.0.0')
    port  = int(os.environ.get('APP_PORT', os.environ.get('PORT', 5000)))
    debug = os.environ.get('FLASK_DEBUG', 'False').lower() in ('1', 'true', 'yes')
    app.run(host=host, port=port, debug=debug)
