# Takeoff Dataiku Integration

## Getting started

This section will assume you are using IntelliJ IDEA as a development
environment.

## Setting up IntelliJ for plugin development

To build the project, install the [ant](https://ant.apache.org/) Intellij
plugin (for more information on the plugin,
Then, in the bar on the right of the page (with an ant on it), click the
settings icon, and in the execution tab, in the "Ant Command Line" text
field, add:
see [here](https://www.jetbrains.com/help/idea/ant.html)).

```
-Denv.DKUINSTALLDIR=/path/to/dataiku/kit
```

(on the ant command line, this can be specified as an environment variable.
`DKUINSTALLDIR=/path/to/dataiku/kit ant ...`)

For example, on my Mac with the dataiku free edition:

```
DKUINSTALLDIR=/Users/fergusbarratt/Library/DataScienceStudio/kits/dataiku-dss
-12.5.1-osx/
```

To give intellij access to the various Dataiku packages, navigate to the
IntelliJ Project Structure modal (`File->Project Structure`, or `CMD-;`). On
this
page
click the plus icon, and add the folders under the `/lib/` and `/dist` paths
in the `DKUINSTALLDIR` above.
![add-packages](docs-images/add-packages.png)

This should be enough to get IntelliJ setup to develop the Dataiku plugin.
To build the package, in the ant sidebar, click the play icon with the `jar`
task highlighted.
![ant-build](docs-images/ant-build.png)
A compiler window should appear.

## Installing the plugin into Dataiku

To use the plugin in Dataiku, it should be installed as a plugin in the
Plugins page. If you develop it in the dataiku dev folder, for me here:

```
/Users/fergusbarratt/Library/DataScienceStudio/dss_home/plugins/dev/titan-ml-connector
```

Then you can make it a development plugin. To make your changes available to
any workflows that use the plugin, you have to recompile the java binary
(with ant, see above),
and then click the reload all button on the dev plugins page.

## Using the plugin in a Dataiku workflow

To use the plugin in Dataiku, you must first have the administrator enable
the LLM that the plugin provides. To do this as an administrator, go to the
`Administrator` page in the dropdown on the top right, and then go to the
connections page. Then add a `Custom LLM` connection. In the configuration
page, make sure to set the endpoint URL for the deployed takeoff instance,
and to choose your plugin as the type.

Then, to use the plugin, upload a dataset to dataiku and then use the LLM
Mesh recipes to define transformations on the dataset. Make sure to use the
model you defined in the previous connection for your LLM task. Embedding
tasks and Generation tasks are supported.

## Setting up Takeoff for use by the plugin

To use the generation and embedding endpoints, make sure that the
corresponding models are setup with consumer groups named: "generator" and
"embedder" as follows:

If they're not in these specifically named consumer groups the plugin won't
work.

```
takeoff:
    server_config:
        max_batch_size: 30
        batch_duration_millis: 50
        echo: false 
        port: 3000
        enable_metrics: true
        heartbeat_check_interval: 5
        Launch_management_server: true
        launch_vertex_server: false
        launch_sagemaker_server: false 
        launch_openai_server: false
        management_port: 3001
        vertex_port: 3002
        openai_port: 3003 
    readers_config:
        reader1:
            model_name: TitanML/llava-1.5-13b-hf-awq
            device: cuda
            consumer_group: generator 
        reader2:
            model_name: "BAAI/bge-large-en-v1.5"
            device: cuda
            consumer_group: embedder
```

