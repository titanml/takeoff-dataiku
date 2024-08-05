<!-- Required extensions: pymdownx.betterem, pymdownx.tilde, pymdownx.emoji, pymdownx.tasklist, pymdownx.superfences -->

# Takeoff Dataiku Plugin

The Takeoff Dataiku Plugin allows you to interaface with TitanML Takeoff via dataiku DSS. This plugin is available from dataiku's [plugin store](https://www.dataiku.com/product/plugins/?filter%5Bplugins-topic%5D=nlp) for versions of DSS >= 12.6.x. Version 12.5.x is supported on request, or you can build the plugin using the instructions below; an example build for 12.5.2 is provided [here](./docs/12.5.2-dss-plugin-titan-ml-connector-1.1.0.zip). This repo contains the plugin's source code, along with contribution information.

> [!IMPORTANT]
> Tutorials and guidance on how to use the plugin can be found [here](https://docs.titanml.co/docs/Docs/integrations/dataiku), or you can see a video demo of the plugin in action [here](https://www.loom.com/share/9c24d2ed5ce94165b76834a068fafd66?sid=de7762cc-229e-4aa8-ad54-28476cb009ab).



### Generating according to a JSON schema or Regex

Dataiku requires these be set at the connection level, with the schema or regex specified input for a given connection and then applied to every inference made with that model.

It's usually worth making two models, each serving as a generator but with one using the required json schema / regex and one which does not.

### Chat Templates

Our Dataiku plugin also supports the use of chat templates. To configure these, create a recipe with an Advanced Prompt which has a list of messages as the `User Message`, just as you would pass to the `inputs` field when using the [native chat template endpoint](https://docs.titanml.co/docs/Docs/interfacing/chat_template).

An example `User Message` would be:
```text
[
    {"role":"system", "content": "You are a helpful assistant whose job is to explain sayings"},
    {"role":"user", "content":"{{text_input_1}}"}
]
```

# Contributing

## Getting started

This section will assume you are using IntelliJ IDEA as a development
environment.

## Building

A makefile is provided to build the plugin. This requires you have [ant](https://docs.jboss.org/jbossas/docs/Getting_Started_Guide/beta422/html/About_the_Example_Applications-Install_Ant.html) installed and on your `PATH`, and have your DSS install directory as the environment variable `DKUINSTALLDIR`.

## Setting up IntelliJ for plugin development

To build the project, install the [ant](https://ant.apache.org/) Intellij
plugin (for more information on the plugin,
Then, in the bar on the right of the page (with an ant on it), click the
settings icon, and in the execution tab, in the "Ant Command Line" text
field, add:
see [here](https://www.jetbrains.com/help/idea/ant.html)).

```
 -Denv.DKUINSTALLDIR=<path/to/dataiku-dss-xx.x.x>
```

For example, on a Mac with the dataiku free edition:

```
DKUINSTALLDIR=/Users/fergusbarratt/Library/DataScienceStudio/kits/dataiku-dss
-12.5.1-osx/
```

Or on linux with a newer version:
```shell
DKUINSTALLDIR=/home/titan-m0/dataiku-dss-12.5.2
```

To give intellij access to the various Dataiku packages, navigate to the
IntelliJ Project Structure modal (`File->Project Structure`, or `CMD-;`). On
this
page
click the plus icon, and add the `/lib/`, `/dist` and `/lib/shadelib` folders
in the `DKUINSTALLDIR` above.
![add-packages](docs/add-packages.png)

You'll also need to install `gson` from maven (Press + -> From Maven -> Search for gson). 
An example of what your .idea folder should now look like is available in example_idea.md.

This should be enough to get IntelliJ setup to develop the Dataiku plugin.
To build the package, in the ant sidebar, click the play icon with the `jar`
task highlighted.
![ant-build](docs/ant-build.png)
A compiler window should appear.

## Installing the plugin into Dataiku

To use the plugin in Dataiku, it should be installed as a plugin in the
Plugins page. If you develop it in the dataiku dev folder, for OSX that'll look something like:

```
/Users/fergusbarratt/Library/DataScienceStudio/dss_home/plugins/dev/titan-ml-connector
```

Or an example for linux:
```shell
/home/titan-0/takeoff-dataiku/titan-ml-connector

```

Alternatively, you can just make symlink from your development environment to the plugins/dev folder.

Then you can make it a development plugin. To make your changes available to
any workflows that use the plugin, you have to recompile the java binary
(with ant, see above),
and then click the reload all button on the dev plugins page.
