PLUGIN_ID=`cat plugin.json | python -c "import sys, json; print(str(json.load(sys.stdin)['id']).replace('/',''))"`
PLUGIN_VERSION=`cat plugin.json | python -c "import sys, json; print(str(json.load(sys.stdin)['version']).replace('/',''))"`

build-plugin:
	ant -f build.xml -Denv.DKUINSTALLDIR=${DATAIKU_INSTALL}
	@cat plugin.json|json_pp > /dev/null

zip-plugin:
	@rm -rf dist
	@mkdir dist
	@zip -r dist/dss-plugin-${PLUGIN_ID}-${PLUGIN_VERSION}.zip plugin.json java-lib

plugin: build-plugin zip-plugin