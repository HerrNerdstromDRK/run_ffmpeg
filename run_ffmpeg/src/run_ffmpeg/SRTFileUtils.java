package run_ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * A set of utility methods for SubRip subtitle files.
 */
public class SRTFileUtils
{
	protected Logger log = null ;
	protected Common common = null ;

	public SRTFileUtils( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;
	}

	/**
	 * Return the first numMinutes of the srt file. To retrieve the entire file, set numMinutes to -1 (or use readSRTFile()).
	 * @param inputFile
	 * @param numMinutes
	 * @return The text associated with the first numMinutes minutes of the srt file or null if a problem occurs.
	 */
	public String getFirstMinutesOfSRTFile( final File inputFile, final int numMinutes )
	{
		assert( inputFile != null ) ;
		assert( inputFile.exists() ) ;

		// MINUTES_IN_MILLIS is the number of milliseconds of the srt file to read.
		// If numMinutes is -1, then read the entire file.
		final long MINUTES_IN_MILLIS = (numMinutes != -1) ? (numMinutes * 60 * 1000) : Long.MAX_VALUE ; // milliseconds

		StringBuilder result = new StringBuilder() ;
		try( BufferedReader reader = new BufferedReader(
				new FileReader( inputFile.getAbsolutePath() ) ) )
		{
			String line = null ;
			//			long currentTimeMillis = 0;

			while( (line = reader.readLine()) != null )
			{
				// Skip blank lines
				if( line.trim().isEmpty() )
				{
					continue ;
				}

				// SRT files are broken into blocks that each look like this:
				// 1 // the segment number
				// 00:00:12,923 --> 00:00:14,987 // start and end time for this subtitle
				// The following takes place between 5 a.m. // subtitle text

				// Check for subtitle number
				if( isInteger( line.trim() ) )
				{
					// Got the beginning of a segment
					// Read the timecode line
					final String timecodeLine = reader.readLine() ;
					if( timecodeLine != null )
					{
						final long startTimeMillis = parseTimecode( timecodeLine.split( "-->" )[ 0 ].trim() ) ;

						// If the subtitle starts within the first N minutes
						if( startTimeMillis < MINUTES_IN_MILLIS )
						{
							// Read subtitle text until a blank line
							while( (line = reader.readLine()) != null && !line.trim().isEmpty() )
							{
								result.append( line ).append(" ") ;
							}
							result.append( System.lineSeparator() ) ; // Add a newline after each subtitle
						}
						else
						{
							// If the subtitle starts after two minutes, we can stop processing
							break ; 
						} // if( startTimeMillis < 2min )
					} // if( timecodeLine != null )
				} // if( isInteger )
			} // while( line = reader )
		} // try
		catch( Exception theException )
		{
			log.warning( "Exception reading " + inputFile.getAbsolutePath() + ": " + theException.toString() ) ;
			result = null ;
		}
		return (null == result) ? null : result.toString().trim() ;
	}

	/**
	 * Read the input file into memory using the srt parser and normalize it.
	 * @param inputFiles
	 * @return
	 */
	public String readAndNormalizeSRTFile( final File inputFile )
	{
		assert( inputFile != null ) ;
		
		final String srtFileData = getFirstMinutesOfSRTFile( inputFile, -1 ) ;
		final String normalizedSRTFileData = normalizeSRTData( srtFileData ) ;

		return normalizedSRTFileData ;
	}

	/**
	 * Read each of the input files into memory using the srt parser and normalize it.
	 * Return a list of Pairs of the form < srtFile, normalizedSRTFileData >.
	 * @param inputFiles
	 * @return
	 */
	public List< Pair< File, String > > readAndNormalizeSRTFiles( final List< File > inputFiles )
	{
		assert( inputFiles != null ) ;
	
		List< Pair< File, String > > srtFilesAndData = new ArrayList< Pair< File, String > >( );
		for( File inputFile : inputFiles )
		{
			try
			{
				final String srtFileData = getFirstMinutesOfSRTFile( inputFile, -1 ) ;
				final String normalizedSRTFileData = normalizeSRTData( srtFileData ) ;
	
				Pair< File, String > pairInfo = Pair.of( inputFile, normalizedSRTFileData ) ;
				srtFilesAndData.add( pairInfo ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Error reading file " + inputFile.getAbsolutePath() + ": " + theException.toString() ) ;
			}
		}
		return srtFilesAndData ;
	}

	/**
	 * Read the input file into memory using the srt parser.
	 * @param inputFiles
	 * @return
	 */
	public String readSRTFile( final File inputFile )
	{
		assert( inputFile != null ) ;
		
		final String srtFileData = getFirstMinutesOfSRTFile( inputFile, -1 ) ;

		return srtFileData ;
	}
	
	/**
	 * Read each of the input files into memory using the srt parser.
	 * Return a list of Pairs of the form < srtFile, srtData >.
	 * @param inputFiles
	 * @return
	 */
	public List< Pair< File, String > > readSRTFiles( final List< File > inputFiles )
	{
		assert( inputFiles != null ) ;

		List< Pair< File, String > > srtFilesAndData = new ArrayList< Pair< File, String > >( );
		for( File inputFile : inputFiles )
		{
			try
			{
				Pair< File, String > pairInfo = Pair.of( inputFile, getFirstMinutesOfSRTFile( inputFile, -1 ) ) ;
				srtFilesAndData.add( pairInfo ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Error reading file " + inputFile.getAbsolutePath() + ": " + theException.toString() ) ;
			}
		}
		return srtFilesAndData ;
	}

	/**
	 * Return true if the string represents an integer, false otherwise.
	 * @param s
	 * @return
	 */
	protected boolean isInteger( final String s )
	{
		try
		{
			Integer.parseInt( s ) ;
			return true ;
		}
		catch( NumberFormatException e )
		{
			return false ;
		}
	}

	/**
	 * Strip the input data of common punctuation and other subtitle markings to make comparisons between srt files of
	 *  different origins more accurate.
	 * @param inputSRTData
	 * @return
	 */
	public String normalizeSRTData( final String inputSRTData )
	{
		assert( inputSRTData != null ) ;
		
		String normalizedSRTData = inputSRTData.replace( "<i>", "" ).replace( "</i>", "" ).replace( "\\[.*\\]", "" ).toLowerCase() ;
		normalizedSRTData = StringUtils.normalizeSpace( normalizedSRTData ) ;
		return normalizedSRTData ;
	}

	/**
	 * Parse a timecode string of the format: 00:00:03,437 and return the time in milliseconds.
	 * @param timecode
	 * @return
	 */
	protected long parseTimecode( final String timecode )
	{
		assert( timecode != null ) ;

		final String[] parts = timecode.split( ":" ) ;
		final int hours = Integer.parseInt( parts[ 0 ] ) ;
		final int minutes = Integer.parseInt( parts[ 1 ] ) ;
		final String[] secondsAndMillis = parts[ 2 ].split( "," ) ;
		final int seconds = Integer.parseInt( secondsAndMillis[ 0 ] ) ;
		final int milliseconds = Integer.parseInt( secondsAndMillis[ 1 ] ) ;

		return (long) hours * 3600 * 1000 + 
				(long) minutes * 60 * 1000 + 
				(long) seconds * 1000 + 
				milliseconds ;
	}
}
