# My Personal Feed Backend
[![Travis CI Status](https://travis-ci.org/lpx1233/personal-feed-backend.svg?branch=master)](https://travis-ci.org/lpx1233/personal-feed-backend) [![Actions Status](https://github.com/lpx1233/personal-feed-backend/workflows/Scala%20CI/badge.svg)](https://github.com/lpx1233/personal-feed-backend/actions)

## Usage
```
git clone https://github.com/lpx1233/personal-feed-backend.git
sbt run
```

## Implemented
* Application framework with Akka (Actor, Http) and MongoDB
* Hacker News crawler
* Web server using Akka Http (REST & GraphQL)
* GraphQL-Playground integration

## TODOs
* User-based recommendation mechanism
* Support other tech news website (Maybe with the help of [News API](https://newsapi.org/))
* Feed ranking algorithm with different news source
