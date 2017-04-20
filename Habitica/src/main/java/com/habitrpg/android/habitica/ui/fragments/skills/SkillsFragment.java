package com.habitrpg.android.habitica.ui.fragments.skills;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.habitrpg.android.habitica.R;
import com.habitrpg.android.habitica.callbacks.SkillCallback;
import com.habitrpg.android.habitica.components.AppComponent;
import com.habitrpg.android.habitica.data.UserRepository;
import com.habitrpg.android.habitica.events.SkillUsedEvent;
import com.habitrpg.android.habitica.events.commands.UseSkillCommand;
import com.habitrpg.android.habitica.models.Skill;
import com.habitrpg.android.habitica.models.responses.SkillResponse;
import com.habitrpg.android.habitica.models.user.HabitRPGUser;
import com.habitrpg.android.habitica.ui.activities.SkillMemberActivity;
import com.habitrpg.android.habitica.ui.activities.SkillTasksActivity;
import com.habitrpg.android.habitica.ui.adapter.SkillsRecyclerViewAdapter;
import com.habitrpg.android.habitica.ui.fragments.BaseMainFragment;
import com.habitrpg.android.habitica.ui.helpers.UiUtils;
import com.habitrpg.android.habitica.ui.menu.DividerItemDecoration;

import org.greenrobot.eventbus.Subscribe;

import javax.inject.Inject;

import butterknife.BindView;
import rx.Observable;

public class SkillsFragment extends BaseMainFragment {

    private final int TASK_SELECTION_ACTIVITY = 10;
    private final int MEMBER_SELECTION_ACTIVITY = 11;

    @Inject
    UserRepository userRepository;

    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    SkillsRecyclerViewAdapter adapter;
    private View view;
    private Skill selectedSkill;
    private ProgressDialog progressDialog;

    static public Double round(Double value, int n) {
        return (Math.round(value * Math.pow(10, n))) / (Math.pow(10, n));
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (view == null)
            view = inflater.inflate(R.layout.fragment_skills, container, false);

        adapter = new SkillsRecyclerViewAdapter();
        checkUserLoadSkills();

        this.tutorialStepIdentifier = "skills";
        this.tutorialText = getString(R.string.tutorial_skills);

        return view;
    }

    @Override
    public void injectFragment(AppComponent component) {
        component.inject(this);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView.invalidateItemDecorations();
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL_LIST));
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
    }

    private void checkUserLoadSkills() {
        if (user == null || adapter == null) {
            return;
        }

        adapter.mana = this.user.getStats().getMp();

        Observable.concat(userRepository.getSkills(user).flatMap(Observable::from), userRepository.getSpecialItems(user).flatMap(Observable::from))
                .toList()
                .subscribe(skills -> adapter.setSkillList(skills), throwable -> {});
    }

    @Override
    public void setUser(HabitRPGUser user) {
        super.setUser(user);

        checkUserLoadSkills();
    }

    @Subscribe
    public void onEvent(UseSkillCommand command) {
        Skill skill = command.skill;

        if (skill.isSpecialItem) {
            selectedSkill = skill;
            Intent intent = new Intent(activity, SkillMemberActivity.class);
            startActivityForResult(intent, MEMBER_SELECTION_ACTIVITY);
        } else if (skill.target.equals("task")) {
            selectedSkill = skill;
            Intent intent = new Intent(activity, SkillTasksActivity.class);
            startActivityForResult(intent, TASK_SELECTION_ACTIVITY);
        } else {
            useSkill(skill);
        }
    }

    @Subscribe
    public void onEvent(SkillUsedEvent event) {
        removeProgressDialog();
        Skill skill = event.usedSkill;
        adapter.setMana(event.newMana);
        StringBuilder message = new StringBuilder();
        if (skill.isSpecialItem) {
            message.append(getContext().getString(R.string.used_skill_without_mana, skill.text));
        } else {
            message.append(getContext().getString(R.string.used_skill, skill.text, skill.mana));
        }

        if (event.xp != 0) {
            message.append(" + ").append(round(event.xp, 2)).append(" XP");
        }
        if (event.hp != 0) {
            message.append(" + ").append(round(event.hp, 2)).append(" HP");
        }
        if (event.gold != 0) {
            message.append(" + ").append(round(event.gold, 2)).append(" GP");
        }
        if (activity != null) {
            UiUtils.showSnackbar(activity, activity.getFloatingMenuWrapper(), message.toString(), UiUtils.SnackbarDisplayType.NORMAL);
        }
        userRepository.retrieveUser(false)
                .subscribe(habitRPGUser -> {
                    if (activity != null) {
                        activity.onUserReceived(habitRPGUser);
                    }
                }, throwable -> {
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case (TASK_SELECTION_ACTIVITY): {
                if (resultCode == Activity.RESULT_OK) {
                    useSkill(selectedSkill, data.getStringExtra("task_id"));
                }
                break;
            }
            case (MEMBER_SELECTION_ACTIVITY): {
                if (resultCode == Activity.RESULT_OK) {
                    useSkill(selectedSkill, data.getStringExtra("member_id"));
                }
                break;
            }
        }
    }

    private void useSkill(Skill skill) {
        useSkill(skill, null);
    }

    private void useSkill(Skill skill, @Nullable String taskId) {
        displayProgressDialog();
        Observable<SkillResponse> observable;
        if (taskId != null) {
            observable = userRepository.useSkill(user, skill.key, skill.target, taskId);
        } else {
            observable = userRepository.useSkill(user, skill.key, skill.target);
        }
        observable.subscribe(new SkillCallback(activity, user, skill), throwable -> removeProgressDialog());
    }

    private void displayProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(activity, getContext().getString(R.string.skill_progress_title), null, true);
    }

    private void removeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

	@Override
	public String customTitle() {	return getString(R.string.sidebar_skills);	}

}
