package run_ffmpeg;

/**
 * The purpose of this class is to probe the directory structure, build
 *  the database entries, and check the database for inconsistencies and duplicates.
 */
public class ProbeBuildAndCheck
{
	public static void main( String[] args )
	{
		ProbeDirectories.main( null ) ;
		CheckLogicalIntegrity.main( null ) ;
	}
}
