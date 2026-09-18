content = open('golden_stopwatch.svg').read()
content = content.replace('viewBox="0 0 800 1000" width="800" height="1000"', 'viewBox="-100 50 1000 1000" width="100%" height="100%"')
open('icon.svg', 'w').write(content)
