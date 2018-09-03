<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text"></xsl:output>
  <xsl:template match="/uids">
    <xsl:text>
package org.dcm4che.data;

import java.util.ResourceBundle;

/**
 * @author Gunter Zeilinger &lt;gunterze@gmail.com&gt;
 */
public class UID {

    private static final ResourceBundle rb = 
            ResourceBundle.getBundle("org.dcm4che3.data.UIDNames");

    public static String nameOf(String uid) {
        try {
            return rb.getString(uid);
        } catch (Exception e) {
            return "?";
        }
    }

    public static String forName(String keyword) {
        try {
            return (String) UID.class.getField(keyword).get(null);
        } catch (Exception e) {
            throw new IllegalArgumentException(keyword);
        }
    }

</xsl:text>
    <xsl:apply-templates select="uid" />
    <xsl:text>
}
</xsl:text>
  </xsl:template>
  <xsl:template match="uid">
    <xsl:text>
    /** </xsl:text>
    <xsl:value-of select="text()" />
    <xsl:text>, </xsl:text>
    <xsl:value-of select="@impl" />
    <xsl:text> */
    public static final String </xsl:text>
    <xsl:value-of select="@keyword" />
    <xsl:text> = "</xsl:text>
    <xsl:value-of select="@value" />
    <xsl:text>";
</xsl:text>
  </xsl:template>
</xsl:stylesheet>