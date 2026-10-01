from flask import Blueprint, render_template, redirect, url_for, session
from extensions import db
from models import User, DonorProfile, SeekerRequest, Match

dashboard_bp = Blueprint('dashboard', __name__)


@dashboard_bp.route('/', endpoint='index')
def index():
    if 'user_id' in session:
        return redirect(url_for('dashboard'))
    return render_template('index.html')


@dashboard_bp.route('/home', endpoint='home')
def home():
    if 'user_id' in session:
        return redirect(url_for('dashboard'))
    donor_count    = User.query.filter_by(user_type='donor').count()
    requests_count = SeekerRequest.query.filter_by(status='open').count()
    return render_template('home.html', donor_count=donor_count, requests_count=requests_count)


@dashboard_bp.route('/dashboard', endpoint='dashboard')
def dashboard():
    if 'user_id' not in session:
        return redirect(url_for('login'))
    if session['user_type'] == 'donor':
        return redirect(url_for('donor_dashboard'))
    return redirect(url_for('seeker_dashboard'))


@dashboard_bp.route('/donor/dashboard', endpoint='donor_dashboard')
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


@dashboard_bp.route('/seeker/dashboard', endpoint='seeker_dashboard')
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
