Event-Crawler
=============

Gets events from APIs of eventful, eventbrite and meetup, normalizes them to a particular format and stores them in MongoDB.
This was my first scala project. To use it type on the shell
sbt console
# then on the console
val eg = new crawler.EventGetter("eventbrite")        // get ready for downloading the events of eventbrite, 2 other acceptable parameters are eventful and meetup
val events = eg.getEvents()		// gets events, it might take a while because it needs to get all the events
eg.storeEvents()			// save events in MongoDB
