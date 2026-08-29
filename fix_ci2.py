import re

with open(".github/workflows/android-ci.yml", "r") as f:
    ci = f.read()

ci = ci.replace("uses: gradle/wrapper-validation-action@f9c9c575b8b21b6485636a91ffecd10e558c62f6 # v3.5.0", "uses: gradle/actions/wrapper-validation@v4")

with open(".github/workflows/android-ci.yml", "w") as f:
    f.write(ci)
