# Joins numbers to their units with a no-break space in text nodes of HTML files.
import re, sys
for p in sys.argv[1:]:
    s = open(p, encoding='utf-8').read()
    head, sep, body = s.partition('<body>')
    body = re.sub(r'(>[^<]*)', lambda m: re.sub(r'(\d) (mg|h|min|HP|hits?|days?|weeks?|entries)\b', '\\1&nbsp;\\2', m.group(1)), body)
    open(p, 'w', encoding='utf-8').write(head + sep + body)
    print('nbsp', p)
