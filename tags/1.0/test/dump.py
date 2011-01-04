#!/usr/bin/python

import struct, sys

ov2 = "ES_R_Fijos_100.ov2"
upi = "ES_R_Fijos_100.upi"

#ov2 = "_TP_ES_R_Fijos_100.ov2"
#upi = "_TP_ES_R_Fijos_100.upi"


def le16tostr(le16):
    out = ""
    lenstr = len(le16) - 2
    for i, ch in enumerate(le16):
        if i % 2 != 0 or i >= lenstr: continue
        out += ch
    return out

recs_ov2 = []
pois_ov2 = 0
skip_ov2 = 0
# Dump TomTom records
with open(ov2, "rb") as f:
    recno = 0
    while True:
        typebyte = f.read(1)
        if typebyte == "":
            break
        type = ord(typebyte)
        if type == 0:           # Deleted record
            skip, = struct.unpack('<i', f.read(4)) - 1 - 4
            if skip > 0:
                f.read(skip)
            print "%.3d - Deleted record - Skipping %d bytes"%(recno, skip)
        elif type == 1:         # Skipper record
            size, = struct.unpack('<i', f.read(4))
            x1, = struct.unpack('<i', f.read(4))
            y1, = struct.unpack('<i', f.read(4))
            x2, = struct.unpack('<i', f.read(4))
            y2, = struct.unpack('<i', f.read(4))
            recs_ov2.append({ 'type': type, 'size': size, 'x1': x1, 'y1': y1, 'x2': x2, 'y2': y2 })
            #print "%.3d - Skipper record - Size: %d X1: %d Y1: %d X2: %d Y2: %d"%(recno, size, x1, y1, x2, y2)
            skip_ov2 += 1
        elif type in (2, 3):    # Simple/extended record
            size, = struct.unpack('<i', f.read(4))
            lon, = struct.unpack('<i', f.read(4))
            lat, = struct.unpack('<i', f.read(4))
            desc = f.read(size - 1 - 12)
            recs_ov2.append({ 'type': type, 'size': size, 'lon': lon, 'lat': lat, 'desc': desc[:-1] })
            #print "%.3d - Simple/extended record - Size: %d Lon: %d Lat: %d Str: %s"%(recno, size, lon, lat, desc)
            pois_ov2 += 1
        else:
            print "%.3d - ERROR Unknown record type: %d"%(recno, type)
            sys.exit(1)
        recno += 1

for i, rec in enumerate(recs_ov2):
    if rec['type'] == 1:
        print "%.3d - Skipper record - X1: %d Y1: %d X2: %d Y2: %d"%(i, rec['x1'], rec['y1'], rec['x2'], rec['y2'])
    elif rec['type'] == 2:
        print "%.3d - Simple/extended record - Lon: %d Lat: %d Str: %s"%(i, rec['lon'], rec['lat'], rec['desc'])

print "OV2 Totals POIs: %d Skipper: %d"%(pois_ov2, skip_ov2)

recs_upi = []
pois_upi = 0
skip_upi = 0
# Dump Sygic records
with open(upi, "rb") as f:
    # First byte is the size of the name
    size = ord(f.read(1))
    name = f.read(size)
    print "Name: %s"%name
    filler = f.read(10)
    print "Filler record: %s"%filler
    size = ord(f.read(1))
    name = f.read(size)
    print "Bitmap Name: %s"%name
    recno = 0
    while True:
        typebyte = f.read(1)
        if typebyte == "":
            break
        type = ord(typebyte)
        if type == 1:         # Skipper record
            size, = struct.unpack('<i', f.read(4))
            x1, = struct.unpack('<i', f.read(4))
            y1, = struct.unpack('<i', f.read(4))
            x2, = struct.unpack('<i', f.read(4))
            y2, = struct.unpack('<i', f.read(4))
            recs_upi.append({ 'type': type, 'size': size, 'x1': x1, 'y1': y1, 'x2': x2, 'y2': y2 })
            #print "%.3d - Skipper record - Size: %d X1: %d Y1: %d X2: %d Y2: %d"%(recno, size, x1, y1, x2, y2)
            skip_upi += 1
        elif type == 3:       # Simple/extended record
            size, = struct.unpack('<i', f.read(4))
            lon, = struct.unpack('<i', f.read(4))
            lat, = struct.unpack('<i', f.read(4))
            desc = le16tostr(f.read(size - 1 - 12))
            recs_upi.append({ 'type': type, 'size': size, 'lon': lon, 'lat': lat, 'desc': desc })
            #print "%.3d - Simple/extended record - Size: %d Lon: %d Lat: %d Str: %s"%(recno, size, lon, lat, desc)
            pois_upi += 1
        else:
            print "%.3d - ERROR Unknown record type: %d"%(recno, type)
            sys.exit(1)
        recno += 1

for i, rec in enumerate(recs_upi):
    if rec['type'] == 1:
        print "%.3d - Skipper record - X1: %d Y1: %d X2: %d Y2: %d"%(i, rec['x1'], rec['y1'], rec['x2'], rec['y2'])
    elif rec['type'] == 3:
        print "%.3d - Simple/extended record - Lon: %d Lat: %d Str: %s"%(i, rec['lon'], rec['lat'], rec['desc'])

print "UPI Totals POIs: %d Skipper: %d"%(pois_upi, skip_upi)
print "\n--------------------------------------------------------------------------------\n"

match = 0
print "Comparing POIs OV2 type 2 vs. UPI type 3"
for i, rec_ov2 in enumerate(recs_ov2):
    if rec_ov2['type'] != 2:
        continue
    for k, rec_upi in enumerate(recs_upi):
        if rec_upi['type'] != 3:
            continue
        if rec_ov2['lat'] == rec_upi['lat'] and rec_ov2['lon'] == rec_upi['lon'] and rec_ov2['desc'] == rec_upi['desc']:
            match += 1
            break
print "Matches: %d"%match
print "\n--------------------------------------------------------------------------------\n"

match = 0
match_dif_x1 = 0
match_dif_x1_y1 = 0
print "Comparing OV2 skipper vs. UPI skipper"
for i, rec_ov2 in enumerate(recs_ov2):
    if rec_ov2['type'] != 1:
        continue
    for k, rec_upi in enumerate(recs_upi):
        if rec_upi['type'] != 1:
            continue
        if rec_ov2['x1'] == rec_upi['x1'] and rec_ov2['y1'] == rec_upi['y1'] and rec_ov2['x2'] == rec_upi['x2'] and rec_ov2['y2'] == rec_upi['y2']:
            match += 1
            break
        elif rec_ov2['x1'] != rec_upi['x1'] and rec_ov2['y1'] == rec_upi['y1'] and rec_ov2['x2'] == rec_upi['x2'] and rec_ov2['y2'] == rec_upi['y2']:
            match_dif_x1 += 1
            break
        elif rec_ov2['x1'] != rec_upi['x1'] and rec_ov2['y1'] != rec_upi['y1'] and rec_ov2['x2'] == rec_upi['x2'] and rec_ov2['y2'] == rec_upi['y2']:
            match_dif_x1_y1 += 1
            break
print "Matches: %d Dif X1: %d Dif X1 & Y1: %d"%(match, match_dif_x1, match_dif_x1_y1)
print "\n--------------------------------------------------------------------------------\n"

print "Comparing record to record"
if len(recs_ov2) != len(recs_upi):
    print "Different records quantity, sorry"
else:
    for i in range(len(recs_ov2)):
        rec_upi = recs_upi[i]
        rec_ov2 = recs_ov2[i]
        if rec_upi['type'] == 1 and rec_ov2['type'] == 1:
            if rec_upi['x1'] == rec_ov2['x1'] and rec_upi['y1'] == rec_ov2['y1'] and rec_upi['x2'] == rec_ov2['x2'] and rec_upi['y2'] == rec_ov2['y2']:
                continue
            print "Difference at record %d"%i
            rec = rec_ov2
            print "    OV2 %.3d - Skipper record - Size: %d X1: %d Y1: %d X2: %d Y2: %d"%(i, rec['size'], rec['x1'], rec['y1'], rec['x2'], rec['y2'])
            rec = rec_upi
            print "    UPI %.3d - Skipper record - Size: %d X1: %d Y1: %d X2: %d Y2: %d"%(i, rec['size'], rec['x1'], rec['y1'], rec['x2'], rec['y2'])
        elif rec_upi['type'] == 3 and rec_ov2['type'] == 2:
            if rec_upi['lat'] == rec_ov2['lat'] and rec_upi['lon'] == rec_ov2['lon'] and rec_upi['desc'] == rec_ov2['desc']:
                continue
            print "Difference at record %d"%i
            rec = rec_ov2
            print "    OV2 %.3d - Simple/extended record - Size: %d Lon: %d Lat: %d Str: %s"%(i, rec['size'], rec['lon'], rec['lat'], rec['desc'])
            rec = rec_upi
            print "    UPI %.3d - Simple/extended record - Size: %d Lon: %d Lat: %d Str: %s"%(i, rec['size'], rec['lon'], rec['lat'], rec['desc'])
    
    print "\n--------------------------------------------------------------------------------\n"

rec3cnt = 0
last1 = None
last3 = None
for rec in recs_upi:
    if rec['type'] == 1:
        if last1 is not None and last3 is not None and rec3cnt == 1:
            a, b, c, d = (last1['x1']-last3['lon'],last1['y1']-last3['lat'],last3['lon']-last1['x2'],last3['lat']-last1['y2'])
            print "Dif X1: %.9d -- Dif Y1: %.9d -- Dif X2: %.9d -- Dif Y2: %.9d -- TOT X: %.9d -- TOT Y: %.9d (lat: %.9d - lon: %.9d - x1: %d y1: %d x2: %d y2: %d)"%(a, b, c, d, a+c, b+d, last3['lat'], last3['lon'], last1['x1'], last1['y1'], last1['x2'], last1['y2'])
        rec3cnt = 0
        last1 = rec
        last3 = None
    elif rec['type'] == 3:
        last3 = rec
        rec3cnt += 1
