"""
LifeLink - Blood Donation Platform
Utility Functions Module

Contains helper functions and utilities for the platform.
"""

from datetime import datetime, timedelta
from config import BLOOD_GROUPS, DONATION_COOLDOWN_DAYS

def can_donate(last_donation_date):
    """
    Check if a donor is eligible to donate based on cooldown period.
    
    Args:
        last_donation_date: datetime object of last donation
        
    Returns:
        bool: True if eligible to donate, False otherwise
    """
    if not last_donation_date:
        return True
    
    days_since_donation = (datetime.utcnow() - last_donation_date).days
    return days_since_donation >= DONATION_COOLDOWN_DAYS

def is_blood_type_compatible(donor_type, recipient_type):
    """
    Check if donor blood type is compatible with recipient blood type.
    
    Args:
        donor_type: Donor's blood type (e.g., 'O+')
        recipient_type: Recipient's blood type (e.g., 'AB+')
        
    Returns:
        bool: True if compatible, False otherwise
    """
    # Blood type compatibility matrix
    compatibility = {
        'O+': ['O+', 'A+', 'B+', 'AB+'],
        'O-': ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'],
        'A+': ['A+', 'AB+'],
        'A-': ['A+', 'A-', 'AB+', 'AB-'],
        'B+': ['B+', 'AB+'],
        'B-': ['B+', 'B-', 'AB+', 'AB-'],
        'AB+': ['AB+'],
        'AB-': ['AB+', 'AB-']
    }
    
    return recipient_type in compatibility.get(donor_type, [])

def format_datetime(dt):
    """
    Format datetime for display.
    
    Args:
        dt: datetime object
        
    Returns:
        str: Formatted datetime string
    """
    if not dt:
        return ''
    return dt.strftime('%d-%m-%Y %H:%M')

def get_urgency_color(urgency):
    """
    Get color code for urgency level.
    
    Args:
        urgency: Urgency level ('urgent' or 'normal')
        
    Returns:
        str: Color code
    """
    return '#dc3545' if urgency == 'urgent' else '#17a2b8'

def validate_blood_group(blood_group):
    """
    Validate blood group input.
    
    Args:
        blood_group: Blood group string
        
    Returns:
        bool: True if valid, False otherwise
    """
    return blood_group in BLOOD_GROUPS

def calculate_distance(lat1, lon1, lat2, lon2):
    """
    Calculate distance between two coordinates using Haversine formula.
    
    Args:
        lat1, lon1: Donor coordinates
        lat2, lon2: Seeker coordinates
        
    Returns:
        float: Distance in kilometers
    """
    from math import radians, cos, sin, asin, sqrt
    
    # Convert decimal degrees to radians
    lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])
    
    # Haversine formula
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a))
    r = 6371  # Radius of earth in kilometers
    
    return c * r

def get_days_until_deadline(required_by):
    """
    Calculate days until blood requirement deadline.
    
    Args:
        required_by: Required by datetime
        
    Returns:
        int: Days remaining
    """
    if not required_by:
        return 0
    
    days = (required_by - datetime.utcnow()).days
    return max(0, days)

def format_phone_number(phone):
    """
    Format phone number for display (mask most digits).
    
    Args:
        phone: Phone number string
        
    Returns:
        str: Masked phone number
    """
    if len(phone) < 4:
        return phone
    return phone[:3] + '****' + phone[-3:]
