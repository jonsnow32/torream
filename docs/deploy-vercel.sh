#!/bin/bash
# Script to deploy CSPlayer docs to Vercel
# Usage: ./deploy-vercel.sh

# Exit on error
set -e

# Check if vercel CLI is installed
if ! command -v vercel &> /dev/null
then
    echo "Vercel CLI not found. Installing..."
    npm install -g vercel
fi

# Set working directory to the script location
cd "$(dirname "$0")"

# Deploy to Vercel (production)
vercel --prod
