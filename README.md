# Rate_Limiting_with_Redis_and_Lua_Scripting

Step 1: Project Setup
Initialize a new Spring Boot project using Maven or Gradle. Add the necessary dependencies: spring-boot-starter-web for the REST controller and spring-boot-starter-data-redis for Redis integration. Configure the application to connect to a Redis instance running on localhost:6379.

Step 2: Redis Setup Script
Create a shell script named setup-redis.sh in the root of your project. This script will use Docker to start a Redis container. Make the script executable (chmod +x setup-redis.sh).
