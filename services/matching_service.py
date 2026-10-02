from datetime import datetime, timedelta
from models import db, User, DonorProfile, SeekerRequest, Match


def auto_match_request(seeker_request, threshold=3):
    """
    3-Tier Cascading Donor Matching Algorithm:
    1. Tier 1 (City): Primary search matches available & eligible donors with the exact blood group in the seeker's city.
    2. Tier 2 (State): Secondary fallback if < threshold donors found, expanding to available & eligible donors in the same state.
    3. Tier 3 (Nationwide): Final fallback if still < threshold donors found, matching all available & eligible compatible donors nationwide.
    """
    seeker_user = seeker_request.user
    if not seeker_user:
        seeker_user = db.session.get(User, seeker_request.user_id)

    seeker_city  = seeker_user.city.strip() if seeker_user and seeker_user.city else ""
    seeker_state = seeker_user.state.strip() if seeker_user and seeker_user.state else ""

    matched_donors    = []
    matched_donor_ids = set()

    cutoff = datetime.utcnow() - timedelta(days=DonorProfile.MIN_DONATION_INTERVAL_DAYS)
    eligible_filter = db.or_(
        DonorProfile.last_donation_date.is_(None),
        DonorProfile.last_donation_date <= cutoff
    )

    # Tier 1: Same Blood Group + Same City
    if seeker_city:
        city_donors = DonorProfile.query.filter(
            DonorProfile.blood_group == seeker_request.blood_group,
            DonorProfile.available_to_donate.is_(True),
            eligible_filter
        ).join(User).filter(
            User.id != seeker_request.user_id,
            User.city.ilike(seeker_city)
        ).all()

        for dp in city_donors:
            if dp.user_id not in matched_donor_ids:
                matched_donors.append(dp)
                matched_donor_ids.add(dp.user_id)

    # Tier 2: Same Blood Group + Same State
    if len(matched_donors) < threshold and seeker_state:
        state_donors = DonorProfile.query.filter(
            DonorProfile.blood_group == seeker_request.blood_group,
            DonorProfile.available_to_donate.is_(True),
            eligible_filter
        ).join(User).filter(
            User.id != seeker_request.user_id,
            User.state.ilike(seeker_state)
        ).all()

        for dp in state_donors:
            if dp.user_id not in matched_donor_ids:
                matched_donors.append(dp)
                matched_donor_ids.add(dp.user_id)

    # Tier 3: Same Blood Group Nationwide
    if len(matched_donors) < threshold:
        nationwide_donors = DonorProfile.query.filter(
            DonorProfile.blood_group == seeker_request.blood_group,
            DonorProfile.available_to_donate.is_(True),
            eligible_filter
        ).join(User).filter(
            User.id != seeker_request.user_id
        ).all()

        for dp in nationwide_donors:
            if dp.user_id not in matched_donor_ids:
                matched_donors.append(dp)
                matched_donor_ids.add(dp.user_id)

    # Upsert Match entries
    for dp in matched_donors:
        existing = Match.query.filter_by(request_id=seeker_request.id, donor_id=dp.user_id).first()
        if not existing:
            db.session.add(Match(request_id=seeker_request.id, donor_id=dp.user_id))

    db.session.commit()
    return matched_donors


def process_accept_match(donor_user_id, target_id):
    # 1. Check if target_id corresponds to an existing Match record owned by this donor
    match = Match.query.filter_by(id=target_id, donor_id=donor_user_id).first()

    # 2. If no Match row exists by match_id, check if target_id refers to an open SeekerRequest
    if not match:
        sr = db.session.get(SeekerRequest, target_id)
        if not sr or sr.status != 'open':
            return {'error': 'Request or match not found'}, 404

        dp = DonorProfile.query.filter_by(user_id=donor_user_id).first()
        if not dp or dp.blood_group != sr.blood_group:
            return {'error': 'Blood group mismatch'}, 400

        if not dp.is_eligible_to_donate:
            return {'error': 'You are not currently eligible to donate blood (minimum 90 days required between donations).'}, 400

        match = Match.query.filter_by(request_id=sr.id, donor_id=donor_user_id).first()
        if not match:
            match = Match(request_id=sr.id, donor_id=donor_user_id)
            db.session.add(match)

    match.status         = 'accepted'
    match.contact_shared = True
    db.session.commit()

    sr = match.request
    return {
        'success':      True,
        'match_id':     match.id,
        'seeker_name':  sr.user.full_name,
        'seeker_phone': sr.user.phone,
        'hospital':     sr.hospital_name
    }, 200
