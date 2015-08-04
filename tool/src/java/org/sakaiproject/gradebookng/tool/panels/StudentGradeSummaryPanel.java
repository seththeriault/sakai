package org.sakaiproject.gradebookng.tool.panels;

import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.gradebookng.business.GradebookNgBusinessService;
import org.sakaiproject.gradebookng.business.model.GbGradeInfo;
import org.sakaiproject.gradebookng.business.model.GbStudentGradeInfo;
import org.sakaiproject.gradebookng.tool.pages.GradebookPage;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.service.gradebook.shared.CategoryDefinition;

/**
 * 
 * Cell panel for the student grade summary
 * 
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 *
 */
public class StudentGradeSummaryPanel extends Panel {

	private static final long serialVersionUID = 1L;
	
	private GbStudentGradeInfo gradeInfo;
	private List<CategoryDefinition> categories;
	private ModalWindow window;
	
	@SpringBean(name="org.sakaiproject.gradebookng.business.GradebookNgBusinessService")
	protected GradebookNgBusinessService businessService;
	
	public StudentGradeSummaryPanel(String id, IModel<Map<String,Object>> model, ModalWindow window) {
		super(id, model);
		
		this.window = window;
	}
	
	@Override
	public void onInitialize() {
		super.onInitialize();
		
		//unpack model
		Map<String,Object> modelData = (Map<String,Object>) this.getDefaultModelObject();
		String userId = (String) modelData.get("userId");
		String displayName = (String) modelData.get("displayName");
		
		//build the grade matrix for the user
        final List<Assignment> assignments = this.businessService.getGradebookAssignments();
        
		//TODO catch if this is null, the get(0) will throw an exception
        //TODO also catch the GbException
        this.gradeInfo = this.businessService.buildGradeMatrix(assignments, Collections.singletonList(userId)).get(0);
		this.categories = this.businessService.getGradebookCategories();

		final List<String> categoryNames = new ArrayList<String>();
		final Map<String, List<Assignment>> categoriesToAssignments = new HashMap<String, List<Assignment>>();

		Iterator<Assignment> assignmentIterator = assignments.iterator();
		while (assignmentIterator.hasNext()) {
			Assignment assignment = assignmentIterator.next();
			String category = assignment.getCategoryName() == null ? GradebookPage.UNCATEGORIZED : assignment.getCategoryName();

			if (!categoriesToAssignments.containsKey(category)) {
				categoryNames.add(category);
				categoriesToAssignments.put(category, new ArrayList<Assignment>());
			}

			categoriesToAssignments.get(category).add(assignment);
		}

		Collections.sort(categoryNames);

		add(new ListView<String>("categoriesList", categoryNames) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<String> categoryItem) {
				final String category = categoryItem.getModelObject();

				categoryItem.add(new Label("category", category));

				CategoryDefinition categoryDefinition = null;
				for (CategoryDefinition aCategoryDefinition : categories) {
					if (aCategoryDefinition.getName().equals(category)) {
						categoryDefinition = aCategoryDefinition;
						break;
					}
				}

				if (categoryDefinition != null) {
					Double score = gradeInfo.getCategoryAverages().get(categoryDefinition.getId());
					String grade = "";
					if (score != null) {
						grade = CategoryColumnCellPanel.formatDouble(score);
					}
					categoryItem.add(new Label("categoryGrade", grade));

					String weight = "";
					if (categoryDefinition.getWeight() == null) {
						weight = CategoryColumnCellPanel.formatDouble(categoryDefinition.getWeight());
					}
					categoryItem.add(new Label("categoryWeight", weight));
				} else {
					categoryItem.add(new Label("categoryGrade", ""));
					categoryItem.add(new Label("categoryWeight", ""));
				}

				categoryItem.add(new ListView<Assignment>("assignmentsForCategory", categoriesToAssignments.get(category)) {
					private static final long serialVersionUID = 1L;

					@Override
					protected void populateItem(ListItem<Assignment> assignmentItem) {
						final Assignment assignment = assignmentItem.getModelObject();

						GbGradeInfo gradeInfo = StudentGradeSummaryPanel.this.gradeInfo.getGrades().get(assignment.getId());

						final String rawGrade;
						String comment;
						if(gradeInfo != null) {
							rawGrade = gradeInfo.getGrade();
							comment = gradeInfo.getGradeComment();
						} else {
							rawGrade = "";
							comment = "";
						}

						assignmentItem.add(new Label("title", assignment.getName()));
						assignmentItem.add(new WebMarkupContainer("isExtraCredit") {
							@Override
							public boolean isVisible() {
								return assignment.getExtraCredit();
							}
						});
						assignmentItem.add(new WebMarkupContainer("isNotCounted") {
							@Override
							public boolean isVisible() {
								return !assignment.isCounted();
							}
						});
						assignmentItem.add(new WebMarkupContainer("isNotReleased") {
							@Override
							public boolean isVisible() {
								return !assignment.isReleased();
							}
						});
						assignmentItem.add(new Label("dueDate", StudentGradeSummaryPanel.this.formatDueDate(assignment.getDueDate())));
						assignmentItem.add(new Label("grade", StudentGradeSummaryPanel.this.formatGrade(rawGrade)));
						assignmentItem.add(new Label("outOf",  new StringResourceModel("label.studentsummary.outof", null, new Object[] { assignment.getPoints() })) {
							@Override
							public boolean isVisible() {
								return rawGrade != "";
							}
						});
						assignmentItem.add(new Label("weight", assignment.getWeight()));
						assignmentItem.add(new Label("comments", comment));
					}
				});
			}
		});

        
        //done button
        add(new AjaxLink<Void>("done") {
	       
			private static final long serialVersionUID = 1L;

			@Override
	        public void onClick(AjaxRequestTarget target){
	            window.close(target);
	        }
	    });
        
      //heading
      add(new Label("heading", new StringResourceModel("heading.studentsummary", null, new Object[]{ displayName })));
			
      //course grade
      add(new Label("courseGrade", this.gradeInfo.getCourseGrade()));

			add(new AttributeModifier("data-studentid", userId));
	}
	
	/**
	 * Format a due date
	 * 
	 * @param assignmentDueDate
	 * @return
	 */
	private String formatDueDate(Date date) {
		//TODO locale formatting via ResourceLoader
		
		if(date == null) {
			return getString("label.studentsummary.noduedate");
		}
		
		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
    	return df.format(date);
	}
	
	/**
	 * Format a grade to remove the .0 if present.
	 * @param grade
	 * @return
	 */
	private String formatGrade(String grade) {
		return StringUtils.removeEnd(grade, ".0");		
	}
	
	
	
	
}
