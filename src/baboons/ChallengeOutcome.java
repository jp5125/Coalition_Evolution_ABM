package baboons;

/*
 * This class is designed to store data regarding the outcome of a coalition challenge. Upon instantiation, it tracks which male is the consort, which males join the coalition,
 * which female is the consort female, and several other variables relating to a particular coalition challenge. After the challenge is over, it record if the coalition was
 * successful, whether or not a disruption occurred (e.g. sneaker male stole female), and which male is the new consort.
 */
public class ChallengeOutcome 
{
	boolean coalitionSucceeded;
	boolean disruptionOccurred;
	Baboon newConsort;
	Baboon targetFemale;
	Baboon oldConsort;
	Baboon coalitionMale1;
	Baboon coalitionMale2;
	double pWin;
	boolean consortRetainedFemale;
	boolean sneakerMatingOccurred;

}
