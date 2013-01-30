-- Event values
CREATE TABLE EVENT_VALUES (
	ID INTEGER NOT NULL AUTO_INCREMENT,
	EVENT INTEGER NOT NULL,
	PROP_NAME VARCHAR(20) NOT NULL,
	PROP_VALUE VARCHAR(80) NOT NULL,
	CONSTRAINT PK_EVENT_VALUES PRIMARY KEY (ID),
	CONSTRAINT UQ_EVENT_VALUES UNIQUE (EVENT, PROP_NAME),
	CONSTRAINT FK_EVENT_VALUES_EVENT FOREIGN KEY (EVENT) REFERENCES EVENTS (ID) ON DELETE SET NULL
);