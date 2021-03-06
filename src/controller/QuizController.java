package controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import main.PersistentState;
import model.Answer;
import model.Category;
import model.GameState;
import model.HallOfFame;
import model.Question;
import model.RoundState;
import model.Settings;
import model.Team;
import ui.ControllerCallback;
import ui.QuizUI;

public class QuizController implements ControllerCallback{

	enum State{
		SHOWING_TITLE_SCREEN,
		EXPECTING_TEAM_NAMES,
		SELECTING_CATEGORY,
		SHOWING_ROUND_OVERVIEW,
		SHOWING_QUESTION,
		SHOWING_SOLUTION,
		SHOWING_WINNER,
		SHOWING_HALL_OF_FAME,
		SHOWING_SETTINGS,
	}

	private static final int SIMULTANEOUS_CATEGORIES = 4;
	private static final boolean REUSE_QUESTIONS = !true;
	private static final boolean REUSE_CATEGORIES = true;

	State controllerState;
	GameState gameState;
	List<Category> categories;
	QuizUI ui;
	HallOfFame hof;
	Map<Category, List<Question>> remainingQuestions;


	Thread timer;
	int team1AnswerIndex, team2AnswerIndex;
	int[] selectedCategoriesIndices=new int[SIMULTANEOUS_CATEGORIES];
	Category[] selectedCategories=new Category[SIMULTANEOUS_CATEGORIES];
	Answer[] permutedAnswers;
	Question currentQuestion;

	public QuizController(List<Category> categories, HallOfFame hof, QuizUI ui){
		this.categories=categories;
		this.ui=ui;
		this.hof=hof;
		ui.setControllerCallback(this);
		remainingQuestions=new HashMap<>();
	}
	public List<Question> getRemainingQuestionsList(Category c) {
		if(PersistentState.settings.isConsumeQuestions()){
			List<Question> ret=remainingQuestions.get(c);
			if(ret==null){
				ret=new ArrayList<>();
				remainingQuestions.put(c, ret);
			}
			if(ret.size()==0)
				ret.addAll(c.getQuestions());
			return ret;
		}else{
			return new ArrayList<>(c.getQuestions());
		}
	}
	private void expectState(State s){
		if(controllerState!=s){
			throw new IllegalStateException("Contoller is in state "+controllerState+", but was expected to be in "+s);
		}
	}
	private void expectState(State... ss){
		for(State s: ss)
			if(controllerState==s)
				return;
		throw new IllegalStateException("Contoller is in state "+controllerState+", but was expected to be in one of "+Arrays.asList(ss));
	}
	@Override
	public void teamNamesEntered(String team1, String team2) {
		expectState(State.EXPECTING_TEAM_NAMES);
		gameState=new GameState(new Team(team1), new Team(team2), categories);
		controllerState=State.SHOWING_ROUND_OVERVIEW;
		Settings s = PersistentState.settings;
		ui.showRoundOverview(gameState, s.getRounds(), s.getQuestionsPerRound());
	}

	@Override
	public void titleScreenDismissed(TitleScreenOption what) {
		expectState(State.SHOWING_TITLE_SCREEN);
		switch(what){
		case SHOW_HALL_OF_FAME:
			controllerState=State.SHOWING_HALL_OF_FAME;
			ui.showHallOfFame(hof, null, null);
			break;
		case START_GAME:
			controllerState=State.EXPECTING_TEAM_NAMES;
			ui.promptForTeamNames();
			break;
		case EDIT_SETTINGS:
			controllerState=State.SHOWING_SETTINGS;
			ui.showSettingsScreen(PersistentState.settings);
			break;	
		}
	}

	@Override
	public void categorySelected(int index) {
		expectState(State.SELECTING_CATEGORY);
		Category selected=selectedCategories[index];
		int selectedIndex=selectedCategoriesIndices[index];
		if(!REUSE_CATEGORIES)
			gameState.removeCategory(selectedIndex);
		gameState.beginNewRound(selected, getRemainingQuestionsList(selected));
		showQuestion();
	}

	private void showQuestion() {
		expectState(State.SELECTING_CATEGORY, State.SHOWING_SOLUTION);
		controllerState=State.SHOWING_QUESTION;
		RoundState rs=gameState.getCurrentRound();
		int qi = rs.selectQuestionIndex();
		final Question q=rs.getQuestion(qi);
		currentQuestion=q;
		if(!REUSE_QUESTIONS)
			rs.removeQuestion(qi);
		long questionDuration=PersistentState.settings.getTimeoutMs();
		ui.setTimerDisplay(questionDuration, questionDuration);
		List<Answer> ppa=q.getShuffledAnswers();
		permutedAnswers=(Answer[]) ppa.toArray(new Answer[ppa.size()]);
		ui.showQuestion(gameState, q, permutedAnswers);
		final long startTime=System.currentTimeMillis();
		final long endTime=startTime+questionDuration;
		team1AnswerIndex=-1;
		team2AnswerIndex=-1;
		synchronized (this) {
			timer=new Thread(() ->{
				while(true){
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
					}
					long timeRemaining ;
					synchronized (QuizController.this) {
						if(timer!=Thread.currentThread())
							return;
						timeRemaining = endTime - System.currentTimeMillis();
						if(timeRemaining<=0){
							timer=null;
							if(PersistentState.settings.isStrictTimeout())
								endQuestion();
							else{
								ui.setTimerDisplay(0, questionDuration);
							}
							return;
						}
						ui.setTimerDisplay(timeRemaining, questionDuration);
					}

				}
			});
			timer.start();
		}
	}

	@Override
	public void cancelGame() {
		controllerState=State.SHOWING_TITLE_SCREEN;
		synchronized (this) {
			if(timer!=null){
				Thread t = timer;
				timer=null;
				if(t!=null)
					t.interrupt();
			}
		}

		ui.showTitleScreen();
	}


	@Override
	public void roundOverviewDismissed() {
		expectState(State.SHOWING_ROUND_OVERVIEW);
		if(gameState.getRounds().size()<PersistentState.settings.getRounds()){
			controllerState=State.SELECTING_CATEGORY;
			gameState.selectRandomCategories(selectedCategoriesIndices);
			for(int i=0; i<SIMULTANEOUS_CATEGORIES; ++i)
				selectedCategories[i]=gameState.getCategory(selectedCategoriesIndices[i]);
			ui.showCategorySelector(gameState.getRounds().size()%2==0, gameState, selectedCategories);
		}else{
			assert(false);
		}
	}

	@Override
	public void team1AnswerEntered(int index) {
		expectState(State.SHOWING_QUESTION);
		if(team1AnswerIndex>=0){
			ui.giveDoubleAnswerMessage(true);
		}else{
			team1AnswerIndex=index;
		}
		checkAnswers();
	}

	@Override
	public void team2AnswerEntered(int index) {
		expectState(State.SHOWING_QUESTION);
		if(team2AnswerIndex>=0){
			ui.giveDoubleAnswerMessage(false);
		}else{
			team2AnswerIndex=index;
		}
		checkAnswers();
	}

	private void checkAnswers() {
		expectState(State.SHOWING_QUESTION);
		if(team1AnswerIndex<0 || team2AnswerIndex<0)
			return;
		synchronized (this) {
			Thread t = timer;
			timer=null;
			if(t!=null)
				t.interrupt();
		}
		endQuestion();
	}
	private void endQuestion() {
		expectState(State.SHOWING_QUESTION);

		RoundState currentRound = gameState.getCurrentRound();
		currentRound.enterTeamAnswer(true,  team1AnswerIndex>=0 && permutedAnswers[team1AnswerIndex].isCorrect());
		currentRound.enterTeamAnswer(false, team2AnswerIndex>=0 && permutedAnswers[team2AnswerIndex].isCorrect());
		controllerState=State.SHOWING_SOLUTION;
		ui.showSolution(gameState, currentQuestion, permutedAnswers, team1AnswerIndex, team2AnswerIndex);
	}

	@Override
	public void solutionScreenDismissed() {
		expectState(State.SHOWING_SOLUTION);
		RoundState currentRound = gameState.getCurrentRound();
		int tac1=currentRound.getTeamAnswerCount(true);
		int tac2=currentRound.getTeamAnswerCount(false);
		assert(tac1==tac2);
		int tac=tac1;
		if(tac>=PersistentState.settings.getQuestionsPerRound()){
			endRound();
		}else{
			showQuestion();
		}
	}

	private void endRound() {
		expectState(State.SHOWING_SOLUTION);
		Settings s = PersistentState.settings;
		if(gameState.getRounds().size()>=s.getRounds()){
			String location=s.getLocation();
			controllerState=State.SHOWING_WINNER;
			int team1Points=gameState.getTeamPoints(true);
			int team2Points=gameState.getTeamPoints(false);
			hof.addEntry(gameState.getTeam(true), team1Points, location);
			hof.addEntry(gameState.getTeam(false), team2Points, location);

			ui.showWinner(
					gameState, 
					s.getRounds(), 
					s.getQuestionsPerRound()
					);
		}else{
			controllerState=State.SHOWING_ROUND_OVERVIEW;
			ui.showRoundOverview(gameState, s.getRounds(), s.getQuestionsPerRound());
		}

	}
	@Override
	public void winnerScreenDismissed() {
		expectState(State.SHOWING_WINNER);
		controllerState=State.SHOWING_HALL_OF_FAME;
		ui.showHallOfFame(hof, gameState.getTeam(true), gameState.getTeam(false));
	}



	@Override
	public void hallOfFameDismissed() {
		expectState(State.SHOWING_HALL_OF_FAME);
		start();
	}

	public void start() {
		synchronized (this) {
			Thread t=timer;
			timer=null;
			if(t!=null)
				t.interrupt();
		}
		controllerState = State.SHOWING_TITLE_SCREEN;
		ui.showTitleScreen();
	}
	@Override
	public void settingsScreenDismissed(Settings newSettings) {
		expectState(State.SHOWING_SETTINGS);
		if(newSettings!=null){
			PersistentState.settings=newSettings;
			try {
				PersistentState.saveState(null);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		controllerState = State.SHOWING_TITLE_SCREEN;
		ui.showTitleScreen();
	}



}
