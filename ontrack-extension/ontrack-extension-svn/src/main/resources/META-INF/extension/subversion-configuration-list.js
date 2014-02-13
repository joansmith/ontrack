define(['crud'], function (crud) {

    return crud.create({
        url: 'ui/extension/svn/configuration',
        itemName: 'subversion.configuration'.loc(),
        itemTemplateId: 'extension/subversion-repository-row',
        itemDialogTemplateId: 'extension/subversion-repository-dialog',
        commands: [ crud.createCommand('subversion.configuration.create'.loc()) ]
    })

});