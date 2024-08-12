#!/usr/bin/env bash
#set -x

if [ $# -ne 2 ]; then
    echo "Usage: $0 <ProcessingScriptName> <GroupName>"
    exit 1
fi

MXJ_PLATFORM_NAME=MX
MXJ_PROCESS_NICK_NAME=MXPROCESSINGSCRIPT
MXJ_JAR_FILELIST=murex.download.guiclient.download
MXJ_POLICY=java.policy
MXJ_BOOT=./mxjboot.jar

if [ "$JAVAHOME" = "" ] ; then
   echo "Please specify the Java environment by setting JAVAHOME"
   exit 1
fi

MX_USER=EOD
MX_PASSWORD=00a3000d00ea004a00f700dd00eb0014004600ee00d300b700da00e100b400c7001100bb008d005d0071001700ed008b007900520047005f00420048004c0089
MX_GROUP=$2


PATH=$JAVAHOME/jre/bin:$JAVAHOME/bin:$PATH

MXJ_SCRIPT_HEADER=$(mktemp)
MXJ_SCRIPT_ANSWER=$(mktemp)
MXJ_SCRIPT_LOG=$(mktemp)

trap '{ rm -f ${MXJ_SCRIPT_HEADER} ${MXJ_SCRIPT_ANSWER} ${MXJ_SCRIPT_LOG}; }' EXIT

cat << EOF > "${MXJ_SCRIPT_HEADER}"
<?xml version="1.0"?>
<!DOCTYPE ProcessingScriptQuery>
<ProcessingScriptQuery>
 <Context>
  <SqlTrace>
   <Level>0</Level>
   <FilePrefix>NoTrace</FilePrefix>
  </SqlTrace>
 </Context>
 <Script>
  <Name>${1}</Name>
  <Predefined>Yes</Predefined>
 </Script>
</ProcessingScriptQuery>
EOF

echo ---------------------
echo Content of Header
echo ---------------------
cat "${MXJ_SCRIPT_HEADER}"

echo "## START  $(basename "$0") $*"

java -cp $MXJ_BOOT \
     -Djava.security.policy=$MXJ_POLICY \
     "-Djava.rmi.server.codebase=http://${MXJ_FILESERVER_HOST}:${MXJ_FILESERVER_PORT}/${MXJ_JAR_FILELIST}" murex.rmi.loader.RmiLoader \
     /MXJ_CLASS_NAME:murex.apps.middleware.client.home.script.XmlRequestScriptShell \
     "/MXJ_SITE_NAME:${MXJ_SITE_NAME}" \
     /MXJ_PLATFORM_NAME:$MXJ_PLATFORM_NAME \
     /MXJ_PROCESS_NICK_NAME:$MXJ_PROCESS_NICK_NAME \
     "/MXJ_SCRIPT_HEADER:${MXJ_SCRIPT_HEADER}" \
     "/MXJ_SCRIPT_ANSWER:${MXJ_SCRIPT_ANSWER}" \
     "/MXJ_SCRIPT_LOG:${MXJ_SCRIPT_LOG}" \
     /MXJ_SCRIPT_FAMILY:Generic \
     /MXJ_SCRIPT_QUERY:applyXmlAction \
     /USER:${MX_USER} \
     /PASSWORD:${MX_PASSWORD} \
     "/GROUP:${MX_GROUP}" &> /dev/null

STATUS=$(xmllint --format "${MXJ_SCRIPT_ANSWER}"|grep -oPm1 '(?<=<Status>)[^<]+')

echo "## ANSWER $(basename "$0") $*"
cat "${MXJ_SCRIPT_ANSWER}"

echo ---------------------
echo Content of Log
echo ---------------------
cat "${MXJ_SCRIPT_LOG}"

case $STATUS in
    Ended_Successfully) RET=0;;
    *) echo "${MXJ_SCRIPT_HEADER}" Failed >&2
       xmllint --format "${MXJ_SCRIPT_ANSWER}";
       xmllint --format "${MXJ_SCRIPT_LOG}";
        RET=1;;
esac

rm -f "${MXJ_SCRIPT_HEADER}" "${MXJ_SCRIPT_ANSWER}" "${MXJ_SCRIPT_LOG}"

echo -e "\n## END    $(basename "$0") $* RC:$RET"
exit $RET
