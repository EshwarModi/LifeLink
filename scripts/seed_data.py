from app import app, db, User, DonorProfile, SeekerRequest
from werkzeug.security import generate_password_hash
from datetime import datetime, timedelta

with app.app_context():
    db.create_all()

    if not User.query.filter_by(username='donor1').first():
        donor = User(
            username='donor1',
            email='donor1@example.com',
            password=generate_password_hash('Password123!'),
            user_type='donor',
            full_name='Jane Donor',
            phone='555-0100',
            city='Springfield',
            state='State'
        )
        db.session.add(donor)
        db.session.commit()
        donor_profile = DonorProfile(
            user_id=donor.id,
            blood_group='O+',
            age=29,
            gender='Female',
            last_donation_date=datetime.utcnow() - timedelta(days=90),
            available_to_donate=True
        )
        db.session.add(donor_profile)
        db.session.commit()
        print('Created donor1')
    else:
        print('donor1 already exists')

    if not User.query.filter_by(username='seeker1').first():
        seeker = User(
            username='seeker1',
            email='seeker1@example.com',
            password=generate_password_hash('Password123!'),
            user_type='seeker',
            full_name='Alex Seeker',
            phone='555-0200',
            city='Springfield',
            state='State'
        )
        db.session.add(seeker)
        db.session.commit()
        seeker_request = SeekerRequest(
            user_id=seeker.id,
            blood_group='O+',
            units_needed=2,
            urgency='urgent',
            reason='Surgery scheduled tomorrow',
            hospital_name='Springfield General Hospital',
            hospital_address='123 Health St',
            required_by=datetime.utcnow() + timedelta(days=1)
        )
        db.session.add(seeker_request)
        db.session.commit()
        print('Created seeker1 and sample request')
    else:
        print('seeker1 already exists')
