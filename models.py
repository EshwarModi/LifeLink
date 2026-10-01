from datetime import datetime
from extensions import db


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
