# Introduction #

It was pretty difficult to find information on the Internet about Sygic's Mobile Maps (SMM) POIs file format (`*`.upi). Different from the TomTom OV2 format which is well documented and easy to find (see http://www.tomtom.com/lib/doc/ttnavsdk3_manual.pdf and http://www.opentom.org/Ov2).<br />
One of the goals of POIMan for Sygic was converting the OV2 to UPI, in order to get the most popular POIs to work in SMM. So, the only way to do it without documentation was comparing an OV2 binary to its already converted UPI (converted using another existing tool -- most people uses PoiEdit http://www.poiedit.com/) and try to discover what the differences are.

# Differences between OV2 and UPI #

  * All the strings in the UPI files seems to be stored as inverted wide chars. By inverted I mean that each character occupies 16 bits, but the byte order is swapped (I guess is like a little-endian wide char string). And all the strings are null-terminated (two null bytes).
  * There is a header in the UPI file. Contrarily to OV2 that starts directly with records, the UPI has a header and the format is the following:
    * First byte is the length of the POI file name (without extension) that will follow this byte including the null character (remember: everything multiplied by 2 since it is wide char)
    * A string with the name of the POI file without extension
    * Something that seems to be a filler record, composed by one byte 0x02 followed by 9 null bytes (0x00)
    * A byte with the length of the BMP file name that will follow this byte (including null character)
    * A string with the BMP file name including the extension
  * The simple POI record in OV2 is the type 0x02 while in the UPI format is type 0x03
  * The skipper record (type 0x01 in both formats) seem pretty much the same in both files, they are 'rectangles' composed by four coordinates (lat1, lon1) (lat2, lon2). However in the OV2 you can find skipper records following other skipper records, like if you subdivide the rectangles into smaller groups where the POIs that will follow belongs, but this does not works well when you convert them to UPI. So, in other words the POIs should be contained in only one rectangle, so the simples solutions was to use the outer rectangle and skip the rest.