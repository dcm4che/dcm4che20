<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text"></xsl:output>
  <xsl:template match="/elements">
    <xsl:text>
package org.dcm4che6.data;

/**
 * @author Gunter Zeilinger &lt;gunterze@gmail.com&gt;
 */
public class Keyword {

    public static String valueOf(int tag) {
        if ((tag &amp; 0x0000FFFF) == 0
                &amp;&amp; (tag &amp; 0xFFFD0000) != 0)
            return "GroupLength";
        if ((tag &amp; 0x00010000) != 0)
            return ((tag &amp; 0x0000FF00) == 0
                    &amp;&amp; (tag &amp; 0x000000F0) != 0)
                  ? "PrivateCreatorID"
                  : "";
        if ((tag &amp; 0xFFFFFF00) == Tag.SourceImageIDs)
            return "SourceImageIDs";
        int tmp = tag &amp; 0xFFE00000;
        if (tmp == 0x50000000 || tmp == 0x60000000)
            tag &amp;= 0xFFE0FFFF;
        else if ((tag &amp; 0xFF000000) == 0x7F000000
                &amp;&amp; (tag &amp; 0xFFFF0000) != 0x7FE00000)
            tag &amp;= 0xFF00FFFF;
        switch (tag) {
</xsl:text>
    <xsl:apply-templates 
      select="//el[@keyword!='' and not(starts-with(@tag,'002804x'))]" />
    <xsl:text>        }
        return "";
    }

}
</xsl:text>
  </xsl:template>
  <xsl:template match="el">
    <xsl:text>        case Tag.</xsl:text>
    <xsl:value-of select="@keyword" />
    <xsl:text>:
            return "</xsl:text>
    <xsl:value-of select="@keyword" />
    <xsl:text>";
</xsl:text>
  </xsl:template>
</xsl:stylesheet>