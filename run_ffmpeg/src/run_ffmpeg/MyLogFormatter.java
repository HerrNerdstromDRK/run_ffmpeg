package run_ffmpeg;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.StringTokenizer;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class MyLogFormatter extends Formatter
{
	private static final String logFormat = "[%1$s] [%2$-7s] %3$s %4$s %n";
	
    @Override
    public String format( LogRecord record )
    {
    	LocalDateTime ldt = LocalDateTime.ofInstant( record.getInstant(), ZoneId.systemDefault()) ;
    	
    	// Preprend a '0' in front of any field that should be two digits
    	String hourString = "" + ldt.getHour() ;
    	if( ldt.getHour() < 10 ) hourString = "0" + hourString ;
    	String minuteString = "" + ldt.getMinute() ;
    	if( ldt.getMinute() < 10 ) minuteString = "0" + minuteString ;
    	String monthString = "" + ldt.getMonthValue() ;
    	if( ldt.getMonthValue() < 10 ) monthString = "0" + monthString ;
    	
    	String dateTimeString = ldt.getYear()
    			+ monthString
    			+ ldt.getDayOfMonth()
        		+ "."
        		+ hourString
        		+ minuteString ;
    			
    	// Remove the package name
    	String className = record.getSourceClassName() ;
    	if( className.contains( "." ) )
    	{
    		StringTokenizer tokens = new StringTokenizer( className, "." ) ;
    		// Skip the first token
    		tokens.nextToken() ;
    		className = tokens.nextToken() ;
    	}
    	
    	return String.format( logFormat,
    			dateTimeString,
    			record.getLevel(),
    			className + "." + record.getSourceMethodName() + ">",
    			record.getMessage() ) ;
    }

}