from flask import Flask, render_template, request, redirect, url_for, session, jsonify
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta
import os

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///lifelink.db'
app.config['SECRET_KEY'] = 'your-secret-key-change-this'
db = SQLAlchemy(app)

# Database Models
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
    blood_group = db.Column(db.String(5), nullable=False)  # A+, A-, B+, B-, O+, O-, AB+, AB-
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

# Routes
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
    
    user = User.query.get(session['user_id'])
    if session['user_type'] == 'donor':
        return redirect(url_for('donor_dashboard'))
    else:
        return redirect(url_for('seeker_dashboard'))

@app.route('/donor/dashboard')
def donor_dashboard():
    if 'user_id' not in session or session['user_type'] != 'donor':
        return redirect(url_for('login'))
    
    user = User.query.get(session['user_id'])
    donor_profile = DonorProfile.query.filter_by(user_id=user.id).first()
    matches = Match.query.filter_by(donor_id=user.id).all()
    
    return render_template('donor.html', user=user, donor_profile=donor_profile, matches=matches)

@app.route('/seeker/dashboard')
def seeker_dashboard():
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return redirect(url_for('login'))
    
    user = User.query.get(session['user_id'])
    requests = SeekerRequest.query.filter_by(user_id=user.id).all()
    
    return render_template('seeker.html', user=user, requests=requests)

@app.route('/seeker/create-request', methods=['POST'])
def create_request():
    if 'user_id' not in session or session['user_type'] != 'seeker':
        return jsonify({'error': 'Unauthorized'}), 401
    
    data = request.get_json()
    required_by = datetime.fromisoformat(data.get('required_by'))
    
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
    
    # Find matching donors
    matching_donors = DonorProfile.query.filter_by(
        blood_group=data.get('blood_group'),
        available_to_donate=True
    ).all()
    
    for donor in matching_donors:
        match = Match(request_id=seeker_request.id, donor_id=donor.user_id)
        db.session.add(match)
    
    db.session.commit()
    return jsonify({'success': True, 'request_id': seeker_request.id}), 200

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
    
    requests = SeekerRequest.query.filter(
        SeekerRequest.blood_group == donor_profile.blood_group,
        SeekerRequest.status == 'open'
    ).all()
    
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
    } for r in requests])

@app.route('/api/accept-match/<int:match_id>', methods=['POST'])
def accept_match(match_id):
    if 'user_id' not in session or session['user_type'] != 'donor':
        return jsonify({'error': 'Unauthorized'}), 401
    
    match = Match.query.get(match_id)
    if not match or match.donor_id != session['user_id']:
        return jsonify({'error': 'Not found'}), 404
    
    match.contact_shared = True
    db.session.commit()
    
    return jsonify({'success': True})

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True)
