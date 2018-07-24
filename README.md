# README #

This connector supports JMS using JNDI as a plugin connector
for Harmony 5.5.

## TL;DR ##

The JMS JNDI connector is distributed as a ZIP archive named
`jmsjndi-5.5.0.0-build-distribution.zip` where `build` is a build number
(of 7 heaxadecimal digits).  It can be installed on a Harmony 5.5 server.

To install the connector, expand the archive from the Harmony 5.5 installation
directory (`$CLEOHOME` below) and restart Harmony.

```
cd $CLEOHOME
unzip -o jmsjndi-5.5.0.0-build-distribution.zip
./Harmonyd stop
./Harmonyd start
```

When Harmony/VLTrader restarts, you will see a new `Template` in the host tree
under `Connections` > `Generic` > `Generic JMSJNDI`.  Select `Clone and Activate`
and a new `JMSJNDI` connection (host) will appear on the `Active` tab.

To configure the new `JMSJNDI` connection, ...

## Connector Actions ##

Actions configured directly for a JMS JNDI connection may directly manipulate the
associated container through _commands_.  The following commands (and options)
are supported:

| Command                              | Options | Description |
|--------------------------------------|---------|-------------|
| `DIR` _directory_                    | &nbsp;  | ... |
| `GET`&nbsp;_name_&nbsp;_destination_ | `-DEL`  | ... |
| `PUT` _source_ _name_                | `-DEL`  | ... |
| `DELETE` _name_                      | &nbsp;  | ... |
| `ATTR` _name_                        | &nbsp;  | ... |

