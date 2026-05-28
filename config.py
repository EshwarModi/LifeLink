"""
LifeLink - Blood Donation Platform
Database Configuration Module

Contains database setup and utilities for the LifeLink platform.
"""

# Database Configuration
SQLALCHEMY_DATABASE_URI = 'sqlite:///lifelink.db'
SQLALCHEMY_TRACK_MODIFICATIONS = False

# Blood Groups Supported
BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-']

# User Types
USER_TYPES = ['donor', 'seeker']

# Request Status
REQUEST_STATUS = ['open', 'fulfilled', 'cancelled']

# Urgency Levels
URGENCY_LEVELS = ['normal', 'urgent']

# Age Restrictions for Donors
MIN_DONOR_AGE = 18
MAX_DONOR_AGE = 65

# Donation Eligibility Rules
DONATION_COOLDOWN_DAYS = 56  # Minimum days between donations
DONATION_MINIMUM_WEIGHT = 50  # kg

# Pagination
ITEMS_PER_PAGE = 10
