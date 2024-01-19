<?xml version="1.0" encoding="windows-1252"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	<xsl:output method="xml" indent="false" omit-xml-declaration="yes"/>
	<xsl:include href="junitTestCaseTemplate.xsl"/>

	<xsl:template match="/" priority="9">
		<testsuite name="org.restcomm.perfcorder.jsip">
			<xsl:for-each select="//key[text()='Cpu']/parent::entry/value/median">
				<xsl:call-template name="lessThanTemplate">
					<xsl:with-param name="caseName" select="'CPUMedian'" />
					<xsl:with-param name="thresholdValue"  select="'67'" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA'" />
				</xsl:call-template>
			</xsl:for-each>
			<xsl:for-each select="//key[text()='Mem']/parent::entry/value/min">
				<xsl:call-template name="lessThanTemplate">
					<xsl:with-param name="caseName" select="'MemMin'" />
					<xsl:with-param name="thresholdValue"  select="'1500'" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA'" />
				</xsl:call-template>
			</xsl:for-each>
			<xsl:for-each select="//key[text()='GcMemAfter']/parent::entry/value/min">
				<xsl:call-template name="lessThanTemplate">
					<xsl:with-param name="caseName" select="'GcMemAfterMin'" />
					<xsl:with-param name="thresholdValue"  select="'1500'" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA'" />
				</xsl:call-template>
			</xsl:for-each>
			<xsl:for-each select="//key[text()='GcPauseDuration']/parent::entry/value/median">
				<xsl:call-template name="lessThanTemplate">
					<xsl:with-param name="caseName" select="'GcPauseDurationMedian'" />
					<xsl:with-param name="thresholdValue"  select="'500'" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA'" />
				</xsl:call-template>
			</xsl:for-each>

			<xsl:for-each select="//key[text()='SIPFailedCalls']/parent::entry/value/sum">
				<xsl:call-template name="ratioLessThanTemplate">
					<xsl:with-param name="caseName" select="'SIPFailedCallsRatio'" />
					<xsl:with-param name="thresholdValue"  select="'0.1'" />
					<xsl:with-param name="measA"  select="//key[text()='SIPFailedCalls']/parent::entry/value/sum" />
					<xsl:with-param name="measB"  select="//key[text()='SIPTotalCallCreated']/parent::entry/value/sum" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA'" />
				</xsl:call-template>
			</xsl:for-each>

			<xsl:for-each select="//key[text()='SIPRetransmissions']/parent::entry/value/sum">
				<xsl:call-template name="ratioLessThanTemplate">
					<xsl:with-param name="caseName" select="'SIPRetransmissionRatio'" />
					<xsl:with-param name="thresholdValue"  select="'0.1'" />
					<xsl:with-param name="measA"  select="//key[text()='SIPRetransmissions']/parent::entry/value/sum" />
					<xsl:with-param name="measB"  select="//key[text()='SIPTotalCallCreated']/parent::entry/value/sum" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA'" />
				</xsl:call-template>
			</xsl:for-each>

			<xsl:for-each select="//key[text()='SIPResponseTime1']/parent::entry/value/percentile95">
				<xsl:call-template name="lessThanTemplate">
					<xsl:with-param name="caseName" select="'SIPResponseTime1Percentile95'" />
					<xsl:with-param name="thresholdValue"  select="'500'" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA'" />
				</xsl:call-template>
			</xsl:for-each>

			<xsl:for-each select="//key[text()='SIPResponseTime2']/parent::entry/value/percentile95">
				<xsl:call-template name="lessThanTemplate">
					<xsl:with-param name="caseName" select="'SIPResponseTime2Percentile95'" />
					<xsl:with-param name="thresholdValue"  select="'500'" />
					<xsl:with-param name="classname"  select="'org.restcomm.perfcorder.jsip.B2BUA1'" />							
				</xsl:call-template>
			</xsl:for-each>


		</testsuite>
	</xsl:template>
</xsl:stylesheet>
