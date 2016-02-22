# Introduction #

In the preferences page, you can configure the URL that will be used to download the POIs list. The URL usually points to an XML file, which schema is pretty much standard. This XML contains the links to the OV2 files (TomTom POIs), BMPs, map and group to which each POI belongs, etc. However, I've found differences regarding the contents of the tags, for example: sometimes the URL to the BMPs are relative to the URL list, sometimes it is the full path; also I've found that some pages may require a user name and password to download the OV2 so in the URL you'll find the %Username% and %Password% variables that POIMan will replace with whatever the user inputs in its preferences page.<br />
In theory, you should be able to use any XML of this page http://www.poiedit.com/sites.htm however, due to these differences I've found, it might not work correctly, let me know if you find any problem.

# Tested XML's #

This is the list of XML's already tested. If you have more to add please let me know.<br />
  * http://www.todo-poi.es/TodoPOI.xml (Spain)
  * http://www.flitspaal.nl/poi_flitspalen.xml (Netherlands) - This one requires to full-fill user name and password. The user name is the e-mail address used to register in the www.flitspaal.nl site.