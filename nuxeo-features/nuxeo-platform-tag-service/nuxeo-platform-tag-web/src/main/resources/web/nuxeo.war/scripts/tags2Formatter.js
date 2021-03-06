function createNewTag(term, data) {
    return {
        id : term,
        displayLabel : term,
        newTag : true
    };
}

function addTagHandler(tag) {
    return addTagging(tag.id);
}

function removeTagHandler(tag) {
    return removeTagging(tag.id);
}

function formatSuggestedTags(tag) {
    if (tag.newTag) {
        return "<span class='s2newTag'>" + tag.displayLabel + "</span>"
    } else {
        return "<span class='s2existingTag'>" + tag.displayLabel + "</span>"
    }
}

function formatSelectedTags(tag) {
    var jsFragment = "listDocumentsForTag('" + tag.displayLabel + "');";
    return '<span class="s2newTag"><a href="' + window.nxContextPath + '/search/tag_search_results.faces?conversationId=' + currentConversationId + '" onclick="' + jsFragment + '">'
            + tag.displayLabel + '</a></span>'
}
