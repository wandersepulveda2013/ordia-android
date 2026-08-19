import re
content = open(".github/workflows/android-ci.yml", "r").read()

# Replace actions to newer versions
content = content.replace("actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.2.2", "actions/checkout@v4")
content = content.replace("actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9 # v4.8.0", "actions/setup-java@v4")
content = content.replace("actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2", "actions/upload-artifact@v4")
content = content.replace("actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093 # v4.3.0", "actions/download-artifact@v4")
content = content.replace("gradle/actions/setup-gradle@017a9effdb900e5b5b2fddfb590a105619dca3c3 # v4.4.0", "gradle/actions/setup-gradle@v4")

# Update gradle/wrapper-validation-action
content = content.replace("gradle/actions/wrapper-validation@v3.5.0", "gradle/actions/wrapper-validation@v4")
content = content.replace("gradle/wrapper-validation-action@v1", "gradle/actions/wrapper-validation@v4")

with open(".github/workflows/android-ci.yml", "w") as f:
    f.write(content)
