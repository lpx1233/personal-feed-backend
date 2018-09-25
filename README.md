# My Personal Feed Backend
[![Build Status](https://travis-ci.org/lpx1233/personal-feed-backend.svg?branch=master)](https://travis-ci.org/lpx1233/personal-feed-backend)

## Usage
```
git clone https://github.com/lpx1233/personal-feed-backend.git
sbt run
```

## Implemented
* Application framework with Akka (Actor, Http) and MongoDB
* Hacker News crawler
* Web server using Akka Http

## TODOs
* Support other tech news website (Maybe with the help of [News API](https://newsapi.org/))
* Feed ranking algorithm with different news source
* Re-design backend HTTP API