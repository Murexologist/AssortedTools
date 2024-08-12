#!/usr/bin/env bash

MXJ_ANT_BUILD_FILE=$(mktemp)
TASK_CONFIG_FILE=$(mktemp)

trap 'rm -f "$TASK_CONFIG_FILE" "$MXJ_ANT_BUILD_FILE"' EXIT

cat << EOF > "$MXJ_ANT_BUILD_FILE"
<project name="SampleTask" basedir="." default="sample">
    <MxInclude MxAnchor="murex.mxres.mxmlexchange.script.exchangeScripts.mxres#LAUNCHTASKS" MxAnchorType="Include"/>
    <MxInclude MxAnchor="murex.mxres.mxmlexchange.script.exchangeScripts.mxres#STOPTASKS" MxAnchorType="Include"/>
    <target name="STOP" description="Stop the tasks">
        <stoptasks platformName="MX" siteName="site1" nickName="MXMLEXCHANGE" userName="" password="" configFile="$TASK_CONFIG_FILE#MYANCHOR"/>
    </target>
    <target name="START" description="Start the tasks">
        <launchtasks platformName="MX" siteName="site1" nickName="MXMLEXCHANGE" userName="" password="" configFile="$TASK_CONFIG_FILE#MYANCHOR"/>
    </target>
</project>
EOF


cat << EOF > "$TASK_CONFIG_FILE"
<?xml version="1.0"?>
<MxAnchors>
    <MxAnchor Code="MYANCHOR">
<workflowFilter>
  <include processing-template="" workflow="Exchange" workflowType="Exchange" task-type="ImportMQ" />
  <include processing-template="" workflow="Exchange" workflowType="Exchange" task-type="ExportMQ" />
 </workflowFilter>
     </MxAnchor>
 </MxAnchors>
EOF

cd
launchmxj.app -scriptant /MXJ_ANT_BUILD_FILE:"$MXJ_ANT_BUILD_FILE" /MXJ_ANT_TARGET:"$1"
