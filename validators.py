import re

VALID_BLOOD_GROUPS = {'A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-'}
VALID_URGENCY     = {'low', 'medium', 'high', 'critical'}
VALID_USER_TYPES  = {'donor', 'seeker'}
VALID_GENDERS     = {'Male', 'Female', 'Other'}


def validate_register(data):
    errors = []
    if not data.get('username') or len(data['username'].strip()) < 3:
        errors.append('Username must be at least 3 characters.')
    if not data.get('email') or not re.match(r'^[^@]+@[^@]+\.[^@]+$', data['email']):
        errors.append('A valid email is required.')
    pwd = data.get('password', '')
    if not pwd or len(pwd) < 8 or not re.search(r'[A-Z]', pwd) or not re.search(r'[a-z]', pwd) or not re.search(r'\d', pwd):
        errors.append('Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, and one number.')
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
