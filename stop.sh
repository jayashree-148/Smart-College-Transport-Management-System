#!/bin/sh
# Stops any OLD CampusTransit server still running.
pkill -f "kalvi.transport.web.WebApp" && echo "Old server stopped." || echo "No old CampusTransit server found."
