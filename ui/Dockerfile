# =========================================================================
# To build this image locally, run the following command from the /ui directory:
#   docker build -t saga-ui:latest .
# =========================================================================

FROM nginx:alpine

# Copy all static UI assets to Nginx default public directory
COPY . /usr/share/nginx/html

# EXPOSE port 80
EXPOSE 80

# When starting the container, write the environment variable BACKEND_API_URL 
# to config.js dynamically, then execute Nginx to serve the site.
CMD ["sh", "-c", "echo \"window.ORDER_SERVICE_API = '${BACKEND_API_URL:-http://localhost:8080}';\" > /usr/share/nginx/html/config.js && exec nginx -g 'daemon off;'"]

# docker build -t justamitsaha/rabbitmq_saga:latest ui/
