## Configuring IntelliJ for development


The use of `$PROJECT_DIR$` throughout here is an inbuilt IntelliJ alias which points to the base of the local copy of the repository (usually `takeoff-dataiku`). `$MAVEN_REPOSITORY$` is similarly pre-defined in intelliJ.

`$DATAIKU_INSTALL$` is a custom __path variable__, a form of environment variable in intelliJ which is automatically substituted into intelliJ config files. It can be set via Settings -> Appearence -> Path Variables. It should be set to the location of your DSS install. 
IntelliJ sometimes needs to be restarted after setting a Path Variable for it to become usable.

Alternatively replace `$DATAIKU_INSTALL$` below with the path of your DSS install.



## Example ant config:

`/.idea/ant.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="AntConfiguration">
    <buildFile url="file://$PROJECT_DIR$/build.xml">
      <antCommandLine value="-Denv.DKUINSTALLDIR=/home/titan-0/dataiku-dss-12.5.2" />
    </buildFile>
  </component>
</project>
```

## Example package files

`/.idea/libraries/lib.xml`
```xml
<component name="libraryTable">
  <library name="lib">
    <CLASSES>
      <root url="file://$DATAIKU_INSTALL$/lib" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
```

`/.idea/libraries/dist.xml`
```xml
<component name="libraryTable">
  <library name="dist">
    <CLASSES>
      <root url="file://$DATAIKU_INSTALL$/dist" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
    <jarDirectory url="file://$DATAIKU_INSTALL$/dist" recursive="false" />
  </library>
</component>
```

`/.idea/libraries/shadelib.xml`
```xml
<component name="libraryTable">
    <library name="shadelib">
        <CLASSES>
            <root url="file://$DATAIKU_INSTALL$/lib/shadelib" />
        </CLASSES>
        <JAVADOC />
        <SOURCES />
        <jarDirectory url="file://$DATAIKU_INSTALL$/lib/shadelib" recursive="false" />
    </library>
</component>
```


`/.idea/libraries/google_code_gson.xml`
```xml

<component name="libraryTable">
    <library name="google.code.gson" type="repository">
        <properties maven-id="com.google.code.gson:gson:2.10.1" />
        <CLASSES>
            <root url="jar://$MAVEN_REPOSITORY$/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar!/" />
        </CLASSES>
        <JAVADOC />
        <SOURCES />
    </library>
</component>
```
