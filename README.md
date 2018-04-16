# Overview
This project contains the important architecture documents for the ADP sub-projects. All sub-projects reference the guidebook and other materials hosted here.

# Guidebook
The [guidebook is an important piece of the ADP documentation](guidebook/guidebook.md) and is created programmatically via [Structurizr](https://structurizr.com/), the architecture diagramming tool.

# Prerequisites
* `JAVA_HOME` pointing to a current JDK 8
* `API_KEY` with the Structurizr API key
* `API_SECRET` with the Structurizr API secret
* `WORKSPACE_ID` with the Structurizr workspace id to publish the diagrams to

# Building
1. `cd guidebook`
1. `./gradlew` will compile the diagram model but **not publish it**
1. `./gradlew run` will compile the diagram model **and publish it**

# Installation
There is nothing to install.

# Tips and Tricks

## Updating The Diagrams
Prior to updating the diagrams, you need to understand the [C4 architecture model](https://structurizr.com/help/c4) and its terminology.  Once you have a grasp of that, simply edit the `Main.groovy` file in the `guidebook` folder and republish.  You will also need to clean up the diagrams from the Structurizr web site and export them to PNG. The images then need to be saved to the `diagrams`, overwriting the appropriate file.

# Troubleshooting

## Cannot Build
As of July 01, 2017, release 1.0.0-RC3 of the structurizr library was missing support for deployment diagrams.  The temporary work around is to clone the [Strcuturizr repository](https://github.com/structurizr/java) and build it by hand.

1. `cd java`
1. `./gradlew build`
1. `./gradlew publishToMavenLocal`

Once the new release is made public, we can skip this step and use the official library.

# Contributing

# License and Credits
